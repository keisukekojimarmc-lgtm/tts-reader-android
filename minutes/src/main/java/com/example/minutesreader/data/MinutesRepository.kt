package com.example.minutesreader.data

import android.content.Context
import com.example.minutesreader.dropbox.DropboxClient
import com.example.minutesreader.dropbox.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 議事録の一覧と本文を端末内にキャッシュする。
 * 同期時は変更（rev が変わった）ファイルだけダウンロードするので、
 * 2 回目以降は速く、オフラインでも読める。
 */
class MinutesRepository(context: Context, private val client: DropboxClient) {

    private val dir = File(context.filesDir, "minutes")
    private val contentDir get() = File(dir, "content").apply { mkdirs() }
    private val indexFile get() = File(dir, "index.json")
    private val memo = ConcurrentHashMap<String, String>()

    private val _entries = MutableStateFlow(loadIndex())
    val entries: StateFlow<List<Minute>> = _entries

    private fun fileFor(id: String) =
        File(contentDir, id.removePrefix("id:").replace(Regex("[^A-Za-z0-9_-]"), "_") + ".md")

    fun cachedContent(id: String): String? =
        memo[id] ?: fileFor(id).takeIf { it.exists() }?.readText()?.also { memo[id] = it }

    suspend fun content(m: Minute): String = withContext(Dispatchers.IO) {
        cachedContent(m.id) ?: client.download(m.id).also { store(m.id, it) }
    }

    private fun store(id: String, text: String) {
        fileFor(id).writeText(text)
        memo[id] = text
    }

    /** @return 新規・更新されたファイル数 */
    suspend fun sync(root: String): Int = withContext(Dispatchers.IO) {
        val remote = client.listMarkdown(root)
        val old = _entries.value.associateBy { it.id }
        val changed = AtomicInteger()
        val gate = Semaphore(4)
        val result = coroutineScope {
            remote.map { f ->
                async {
                    val prev = old[f.id]
                    val cached = cachedContent(f.id)
                    if (prev != null && prev.rev == f.rev && cached != null) {
                        if (prev.name == f.name && prev.path == f.pathDisplay) prev else MinuteParser.build(f, cached)
                    } else {
                        fetch(f, gate, prev, changed)
                    }
                }
            }.awaitAll()
        }
        val keep = result.map { fileFor(it.id).name }.toSet()
        contentDir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
        memo.keys.retainAll(result.map { it.id }.toSet())

        val sorted = result.sortedWith(MinuteParser.ORDER)
        saveIndex(sorted)
        _entries.value = sorted
        changed.get()
    }

    private suspend fun fetch(f: RemoteFile, gate: Semaphore, prev: Minute?, changed: AtomicInteger): Minute =
        try {
            val text = gate.withPermit { client.download(f.id) }
            store(f.id, text)
            changed.incrementAndGet()
            MinuteParser.build(f, text)
        } catch (e: IOException) {
            // 1 件の失敗で全体を止めない。次回の更新で再取得される。
            prev?.copy(rev = "") ?: MinuteParser.build(f, null).copy(rev = "")
        }

    fun matches(m: Minute, q: String): Boolean {
        if (q.isBlank()) return true
        val terms = q.trim().lowercase().split(Regex("\\s+"))
        val head = "${m.title} ${m.name} ${m.category} ${m.participants} ${m.summary}".lowercase()
        val body by lazy { cachedContent(m.id)?.lowercase().orEmpty() }
        return terms.all { head.contains(it) || body.contains(it) }
    }

    fun clear() {
        dir.deleteRecursively()
        memo.clear()
        _entries.value = emptyList()
    }

    private fun loadIndex(): List<Minute> = runCatching {
        val arr = JSONArray(indexFile.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Minute(
                id = o.getString("id"),
                path = o.optString("path"),
                name = o.optString("name"),
                rev = o.optString("rev"),
                serverModified = o.optString("modified"),
                date = o.optString("date").ifEmpty { null },
                time = o.optString("time").ifEmpty { null },
                category = o.optString("category"),
                title = o.optString("title"),
                summary = o.optString("summary"),
                participants = o.optString("participants"),
            )
        }
    }.getOrDefault(emptyList())

    private fun saveIndex(list: List<Minute>) {
        dir.mkdirs()
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id).put("path", m.path).put("name", m.name).put("rev", m.rev)
                    .put("modified", m.serverModified).put("date", m.date ?: "").put("time", m.time ?: "")
                    .put("category", m.category).put("title", m.title).put("summary", m.summary)
                    .put("participants", m.participants),
            )
        }
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(indexFile)
    }
}
