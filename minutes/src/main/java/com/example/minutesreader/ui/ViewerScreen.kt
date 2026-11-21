package com.example.minutesreader.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minutesreader.AppViewModel
import com.example.minutesreader.DocState
import com.example.minutesreader.data.MinuteParser
import org.json.JSONObject
import org.json.JSONTokener

/** 本文の段落タップを JS から受け取る（読み上げ中ならその段落へ移動）。 */
private class TapBridge(private val onTap: (Int) -> Unit) {
    @JavascriptInterface
    fun tap(block: Int) = onTap(block)
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(vm: AppViewModel, id: String) {
    val entries by vm.repo.entries.collectAsStateWithLifecycle()
    val minute = entries.firstOrNull { it.id == id }
    val dark = isSystemInDarkTheme()
    val zoom by vm.prefs.textZoom.collectAsStateWithLifecycle()
    val player by vm.speaker.state.collectAsStateWithLifecycle()
    var retry by remember { mutableIntStateOf(0) }
    val state by produceState<DocState>(DocState.Loading, id, dark, retry) {
        value = DocState.Loading
        value = vm.loadDoc(id, dark)
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showToc by remember { mutableStateOf(false) }
    val bg = MaterialTheme.colorScheme.background.toArgb()

    DisposableEffect(id) { onDispose { vm.speaker.close() } }

    // 読んでいる段落をハイライトして、画面外ならスクロール
    LaunchedEffect(player.block, player.active, webView) {
        webView?.evaluateJavascript("window.rdHl&&rdHl(${if (player.active) player.block else -1});", null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る") } },
                title = {
                    Column {
                        Text(minute?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        if (minute != null) {
                            Text(
                                MinuteParser.shortLabel(minute),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    val s = state
                    if (s is DocState.Loaded) {
                        if (s.doc.toc.isNotEmpty()) {
                            IconButton(onClick = { showToc = true }) { Icon(Icons.AutoMirrored.Filled.List, "目次") }
                        }
                        if (!player.active) {
                            IconButton(onClick = {
                                // 画面に見えている段落から読み上げを始める
                                val wv = webView ?: return@IconButton
                                wv.evaluateJavascript("JSON.stringify({b:rdBlocks(),f:rdFirstVisible()})") { result ->
                                    runCatching {
                                        val o = JSONObject(JSONTokener(result).nextValue() as String)
                                        val arr = o.getJSONArray("b")
                                        val blocks = (0 until arr.length()).map { arr.getString(it) }
                                        vm.startReading(blocks, o.optInt("f", 0), minute?.title.orEmpty())
                                    }
                                }
                            }) { Icon(PlayerIcons.Headphones, "読み上げ") }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (player.active) {
                PlayerBar(
                    st = player,
                    onToggle = vm.speaker::toggle,
                    onPrev = vm.speaker::prev,
                    onNext = vm.speaker::next,
                    onSeek = vm.speaker::seek,
                    onRate = vm::changeRate,
                    onClose = vm.speaker::close,
                )
            }
        },
    ) { pad ->
        when (val s = state) {
            DocState.Loading -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is DocState.Error -> Column(
                Modifier.padding(pad).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = { retry++ }) { Text("再読み込み") }
            }
            is DocState.Loaded -> AndroidView(
                modifier = Modifier.padding(pad).fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.builtInZoomControls = false
                        setBackgroundColor(bg)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url
                                if (url.scheme == "http" || url.scheme == "https") {
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))) }
                                }
                                return true
                            }
                        }
                        addJavascriptInterface(TapBridge(vm::onBlockTapped), "Reader")
                        webView = this
                    }
                },
                update = { wv ->
                    wv.settings.textZoom = zoom
                    val key = s.doc.html.hashCode()
                    if (wv.tag != key) {
                        wv.tag = key
                        wv.loadDataWithBaseURL(null, s.doc.html, "text/html", "utf-8", null)
                    }
                },
            )
        }
    }

    val s = state
    if (showToc && s is DocState.Loaded) {
        ModalBottomSheet(onDismissRequest = { showToc = false }) {
            Text(
                "目次",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                itemsIndexed(s.doc.toc) { i, h ->
                    Text(
                        h.text,
                        style = if (h.level == 2) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (h.level == 2) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (h.level == 2) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                webView?.evaluateJavascript(
                                    "var e=document.getElementById('h$i');if(e)e.scrollIntoView({behavior:'smooth',block:'start'});",
                                    null,
                                )
                                showToc = false
                            }
                            .padding(start = if (h.level == 2) 20.dp else 36.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}
