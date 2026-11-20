package com.example.minutesreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minutesreader.AppViewModel
import com.example.minutesreader.data.Prefs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val root by vm.prefs.rootPath.collectAsStateWithLifecycle()
    val zoom by vm.prefs.textZoom.collectAsStateWithLifecycle()
    val rate by vm.prefs.speechRate.collectAsStateWithLifecycle()
    val count by vm.repo.entries.collectAsStateWithLifecycle()
    var rootEdit by rememberSaveable { mutableStateOf(root) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = { IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る") } },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section("文字の大きさ") {
                Text("本文の例：決定事項と ToDo を確認します。", fontSize = (17 * zoom / 100f).sp)
                Slider(
                    value = zoom.toFloat(),
                    onValueChange = { vm.prefs.setZoom((it / 10).roundToInt() * 10) },
                    valueRange = 80f..200f,
                    steps = 11,
                )
                Text("${zoom}%", style = MaterialTheme.typography.labelMedium)
            }
            Section("読み上げの速さ") {
                Slider(
                    value = rate,
                    onValueChange = { vm.prefs.setRate((it * 10).roundToInt() / 10f) },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                )
                Text("${"%.1f".format(rate)}倍", style = MaterialTheme.typography.labelMedium)
            }
            Section("議事録フォルダ") {
                OutlinedTextField(
                    value = rootEdit, onValueChange = { rootEdit = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("サブフォルダ（年/月）内の .md もすべて読み込みます") },
                )
                Button(
                    enabled = Prefs.normalizeRoot(rootEdit) != root,
                    onClick = { vm.changeRoot(rootEdit); vm.back() },
                ) { Text("変更して読み込み直す") }
            }
            Section("Dropbox") {
                Text("アカウント: ${vm.auth.accountName ?: "（不明）"}", style = MaterialTheme.typography.bodyMedium)
                Text("保存済みの議事録: ${count.size}件（オフラインでも閲覧できます）", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { vm.clearCache(); vm.back() }) { Text("保存データを削除して再取得") }
                OutlinedButton(onClick = { confirmLogout = true }) { Text("ログアウト") }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("ログアウトしますか？") },
            text = { Text("端末に保存した議事録も削除されます。Dropbox 上のファイルは消えません。") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("ログアウト") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
