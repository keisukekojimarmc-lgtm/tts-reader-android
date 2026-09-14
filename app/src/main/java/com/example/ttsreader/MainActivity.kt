package com.example.ttsreader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.ttsreader.service.ReaderAccessibilityService
import com.example.ttsreader.settings.ReaderPreferences
import com.example.ttsreader.ui.HomeScreen
import com.example.ttsreader.ui.SetupScreen
import com.example.ttsreader.ui.theme.TtsReaderTheme
import com.example.ttsreader.util.PermissionUtils

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = ReaderPreferences.get(this)
        setContent {
            TtsReaderTheme {
                TtsReaderApp(prefs = prefs)
            }
        }
    }
}

@Composable
private fun TtsReaderApp(prefs: ReaderPreferences) {
    val context = LocalContext.current
    var accessibilityGranted by remember { mutableStateOf(PermissionUtils.isAccessibilityServiceEnabled(context)) }
    var overlayGranted by remember { mutableStateOf(PermissionUtils.canDrawOverlays(context)) }

    // 設定画面から戻ってきた時に権限状態を再確認する
    LifecycleResumeEffect(Unit) {
        accessibilityGranted = PermissionUtils.isAccessibilityServiceEnabled(context)
        overlayGranted = PermissionUtils.canDrawOverlays(context)
        ReaderAccessibilityService.instance?.updateOverlayVisibility()
        onPauseOrDispose { }
    }

    val openSettings: (Intent) -> Unit = { intent ->
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, e.message ?: "Settings not found", Toast.LENGTH_SHORT).show()
        }
    }
    val openAccessibility = { openSettings(PermissionUtils.accessibilitySettingsIntent()) }
    val openOverlay = { openSettings(PermissionUtils.overlaySettingsIntent(context)) }

    if (!accessibilityGranted || !overlayGranted) {
        SetupScreen(
            accessibilityGranted = accessibilityGranted,
            overlayGranted = overlayGranted,
            onOpenAccessibilitySettings = openAccessibility,
            onOpenOverlaySettings = openOverlay,
        )
    } else {
        HomeScreen(
            prefs = prefs,
            accessibilityGranted = accessibilityGranted,
            overlayGranted = overlayGranted,
            onTestSpeak = {
                val service = ReaderAccessibilityService.instance
                if (service == null) {
                    Toast.makeText(context, R.string.toast_service_not_connected, Toast.LENGTH_SHORT).show()
                } else {
                    service.speakSample()
                }
            },
            onOpenAccessibilitySettings = openAccessibility,
            onOpenOverlaySettings = openOverlay,
        )
    }
}
