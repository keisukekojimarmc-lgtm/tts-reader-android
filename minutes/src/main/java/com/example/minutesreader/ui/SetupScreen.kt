package com.example.minutesreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.minutesreader.AppViewModel
import kotlinx.coroutines.launch

private const val CREATE_APP_URL = "https://www.dropbox.com/developers/apps/create"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(vm: AppViewModel) {
    var appKey by rememberSaveable { mutableStateOf(vm.auth.appKey.orEmpty()) }
    var root by rememberSaveable { mutableStateOf(vm.prefs.rootPath.value) }
    var code by rememberSaveable { mutableStateOf("") }
    var opened by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("はじめに（Dropbox と接続）") }) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Dropbox に保存した議事録（.md）を、スマホで読みやすく表示します。最初の 1 回だけ、次の設定が必要です（無料）。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Step("1. Dropbox で「アプリ」を登録（初回のみ）") {
                Text(
                    "ボタンから Dropbox の開発者ページを開き、次のとおり作成します。\n" +
                        "・Scoped access →「Full Dropbox」を選択\n" +
                        "・名前は任意（例: minutes-viewer-自分の名前）\n" +
                        "・作成後「Permissions」タブで files.metadata.read と files.content.read にチェック →「Submit」\n" +
                        "・「Settings」タブの App key をコピー",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { uri.openUri(CREATE_APP_URL) }) { Text("Dropbox 開発者ページを開く") }
            }
            Step("2. App key と議事録フォルダ") {
                OutlinedTextField(
                    value = appKey, onValueChange = { appKey = it.trim() },
                    label = { Text("App key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = root, onValueChange = { root = it },
                    label = { Text("議事録フォルダ（Dropbox 内のパス）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Step("3. Dropbox でアクセスを許可") {
                Button(
                    enabled = appKey.isNotBlank(),
                    onClick = { error = null; opened = true; uri.openUri(vm.startAuth(appKey)) },
                ) { Text("Dropbox で許可する") }
                Text(
                    "ブラウザで「許可」を押すとコードが表示されます。コピーしてこのアプリに戻ってください。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Step("4. コードを貼り付けてログイン") {
                OutlinedTextField(
                    value = code, onValueChange = { code = it },
                    label = { Text("表示されたコード") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { clipboard.getText()?.text?.let { code = it.trim() } }) { Text("クリップボードから貼り付け") }
                Button(
                    enabled = code.isNotBlank() && !busy && (opened || vm.auth.pendingVerifier != null),
                    onClick = {
                        busy = true; error = null
                        scope.launch {
                            error = vm.finishAuth(code, root)
                            busy = false
                        }
                    },
                ) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("ログイン") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun Step(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
