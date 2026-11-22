package com.example.minutesreader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.minutesreader.data.Minute
import com.example.minutesreader.data.MinuteParser
import com.example.minutesreader.data.MinutesRepository
import com.example.minutesreader.data.Prefs
import com.example.minutesreader.dropbox.AuthStore
import com.example.minutesreader.dropbox.DropboxClient
import com.example.minutesreader.dropbox.DropboxException
import com.example.minutesreader.render.MinutesRenderer
import com.example.minutesreader.render.RenderedDoc
import com.example.minutesreader.tts.SpeechText
import com.example.minutesreader.tts.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface Screen {
    data object Setup : Screen
    data object List : Screen
    data class Viewer(val id: String) : Screen
    data object Settings : Screen
}

sealed interface DocState {
    data object Loading : DocState
    data class Loaded(val doc: RenderedDoc) : DocState
    data class Error(val message: String) : DocState
}

@OptIn(FlowPreview::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val auth = AuthStore(app)
    private val client = DropboxClient(auth)
    val repo = MinutesRepository(app, client)
    val speaker = Speaker.get(app)

    private val _stack = MutableStateFlow<kotlin.collections.List<Screen>>(
        listOf(if (auth.isLoggedIn) Screen.List else Screen.Setup),
    )
    val stack: StateFlow<kotlin.collections.List<Screen>> = _stack

    val syncing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")
    val category = MutableStateFlow<String?>(null)

    val categories: StateFlow<kotlin.collections.List<String>> = repo.entries
        .map { list -> list.map { it.category }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visible: StateFlow<kotlin.collections.List<Minute>> =
        combine(repo.entries, query.debounce(200), category, prefs.sortAsc) { list, q, c, asc ->
            val hit = list.filter { (c == null || it.category == c) && repo.matches(it, q) }
            if (asc) hit.sortedWith(MinuteParser.ORDER_ASC) else hit
        }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, repo.entries.value)

    init {
        if (auth.isLoggedIn) refresh()
    }

    // ---- 並び順・折りたたみ ----

    fun toggleSort() = prefs.setSortAsc(!prefs.sortAsc.value)

    fun toggleMonth(month: String) {
        val c = prefs.collapsed.value
        prefs.setCollapsed(if (month in c) c - month else c + month)
    }

    fun collapseAll() = prefs.setCollapsed(repo.entries.value.map(MinuteParser::monthKey).toSet())

    fun expandAll() = prefs.setCollapsed(emptySet())

    // ---- 画面遷移 ----

    fun navigate(s: Screen) { _stack.value = _stack.value + s }

    fun back() {
        if (_stack.value.size > 1) {
            if (_stack.value.last() is Screen.Viewer) speaker.close()
            _stack.value = _stack.value.dropLast(1)
        }
    }

    fun open(m: Minute) = navigate(Screen.Viewer(m.id))

    // ---- 認証 ----

    /** ブラウザで開く Dropbox の認可 URL を返す。 */
    fun startAuth(appKey: String): String = client.beginAuth(appKey.trim())

    /** @return エラーメッセージ。成功時は null */
    suspend fun finishAuth(code: String, root: String): String? = withContext(Dispatchers.IO) {
        try {
            prefs.setRoot(root)
            client.finishAuth(code)
            withContext(Dispatchers.Main) {
                _stack.value = listOf(Screen.List)
                refresh()
            }
            null
        } catch (e: IOException) {
            e.message ?: "ログインに失敗しました"
        }
    }

    fun logout() {
        speaker.close()
        auth.clearTokens()
        repo.clear()
        _stack.value = listOf(Screen.Setup)
    }

    // ---- 同期 ----

    fun refresh() {
        if (syncing.value) return
        syncing.value = true
        viewModelScope.launch {
            try {
                val firstTime = repo.entries.value.isEmpty()
                val n = repo.sync(prefs.rootPath.value)
                val keys = repo.entries.value.map { it.readKey }.toSet()
                if (firstTime) prefs.markAllReadIfFirst(keys)
                // 内容が更新されたファイル（rev が変わったもの）は未読に戻る
                prefs.retainRead(keys)
                if (!firstTime && n > 0) message.value = "${n}件の議事録を更新しました"
            } catch (e: DropboxException) {
                message.value = e.message
            } catch (e: IOException) {
                message.value = "オフラインのため、保存済みの議事録を表示しています"
            } finally {
                syncing.value = false
            }
        }
    }

    fun changeRoot(path: String) {
        prefs.setRoot(path)
        repo.clear()
        refresh()
    }

    fun clearCache() {
        repo.clear()
        refresh()
    }

    // ---- 閲覧 ----

    suspend fun loadDoc(id: String, dark: Boolean): DocState = withContext(Dispatchers.IO) {
        val m = repo.entries.value.firstOrNull { it.id == id }
            ?: return@withContext DocState.Error("この議事録は見つかりませんでした")
        try {
            val text = repo.content(m)
            prefs.markRead(m)
            DocState.Loaded(MinutesRenderer.render(text, m.title, dark))
        } catch (e: IOException) {
            DocState.Error(e.message ?: "読み込めませんでした。通信状態を確認してください。")
        }
    }

    // ---- 読み上げ ----

    /** 本文の段落リストを受け取り、firstBlock 番目の段落から読み上げを始める。 */
    fun startReading(blocks: kotlin.collections.List<String>, firstBlock: Int, title: String) {
        val items = SpeechText.items(blocks)
        val start = items.indexOfFirst { it.block >= firstBlock }.coerceAtLeast(0)
        speaker.load(items, start, prefs.speechRate.value, title)
    }

    /** 本文の段落がタップされたとき（読み上げ中ならそこへ移動）。 */
    fun onBlockTapped(block: Int) {
        if (speaker.state.value.active) speaker.seekBlock(block)
    }

    fun changeRate(rate: Float) {
        val r = (Math.round(rate.coerceIn(0.5f, 3.0f) * 10) / 10f)
        prefs.setRate(r)
        speaker.setRate(r)
    }

    override fun onCleared() {
        // アプリを終了したら読み上げも終える
        speaker.close()
    }
}
