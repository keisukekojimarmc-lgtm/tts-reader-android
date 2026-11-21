package com.example.ttsreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ttsreader.R
import com.example.ttsreader.reading.ReaderState
import com.example.ttsreader.reading.ReaderStateHolder
import com.example.ttsreader.reading.TtsStatus
import com.example.ttsreader.settings.ReaderPreferences

/**
 * 権限が揃った後のメイン画面。状態表示・読み上げ設定・使い方を出す。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefs: ReaderPreferences,
    accessibilityGranted: Boolean,
    overlayGranted: Boolean,
    onTestSpeak: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
) {
    val speechRate by prefs.speechRate.collectAsStateWithLifecycle()
    val pitch by prefs.pitch.collectAsStateWithLifecycle()
    val overlayEnabled by prefs.overlayEnabled.collectAsStateWithLifecycle()
    val autoLanguage by prefs.autoLanguage.collectAsStateWithLifecycle()
    val readerState by ReaderStateHolder.state.collectAsStateWithLifecycle()
    val serviceConnected by ReaderStateHolder.serviceConnected.collectAsStateWithLifecycle()
    val ttsStatus by ReaderStateHolder.ttsStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(
                serviceConnected = serviceConnected,
                readerState = readerState,
                ttsStatus = ttsStatus,
            )

            SectionTitle(stringResource(R.string.home_speech_rate, speechRate))
            Slider(
                value = speechRate,
                onValueChange = prefs::setSpeechRate,
                valueRange = ReaderPreferences.MIN_SPEECH_RATE..ReaderPreferences.MAX_SPEECH_RATE,
                steps = 14,
            )

            SectionTitle(stringResource(R.string.home_pitch, pitch))
            Slider(
                value = pitch,
                onValueChange = prefs::setPitch,
                valueRange = ReaderPreferences.MIN_PITCH..ReaderPreferences.MAX_PITCH,
                steps = 14,
            )

            OutlinedButton(
                onClick = onTestSpeak,
                enabled = serviceConnected && ttsStatus is TtsStatus.Ready,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_test_speak))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_overlay_switch),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = overlayEnabled, onCheckedChange = prefs::setOverlayEnabled)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_auto_language_switch),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = autoLanguage, onCheckedChange = prefs::setAutoLanguage)
            }

            SectionTitle(stringResource(R.string.home_howto_title))
            Text(
                text = stringResource(R.string.home_howto_body),
                style = MaterialTheme.typography.bodyMedium,
            )

            SectionTitle(stringResource(R.string.home_permissions_title))
            PermissionCard(
                title = stringResource(R.string.setup_accessibility_title),
                body = stringResource(R.string.setup_accessibility_body),
                granted = accessibilityGranted,
                buttonLabel = stringResource(R.string.setup_open_accessibility),
                onClick = onOpenAccessibilitySettings,
            )
            PermissionCard(
                title = stringResource(R.string.setup_overlay_title),
                body = stringResource(R.string.setup_overlay_body),
                granted = overlayGranted,
                buttonLabel = stringResource(R.string.setup_open_overlay),
                onClick = onOpenOverlaySettings,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun StatusCard(
    serviceConnected: Boolean,
    readerState: ReaderState,
    ttsStatus: TtsStatus,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionTitle(stringResource(R.string.home_status_title))
            Text(
                stringResource(
                    if (serviceConnected) R.string.home_service_connected else R.string.home_service_disconnected,
                ),
            )
            Text(
                stringResource(
                    when (readerState) {
                        ReaderState.READING -> R.string.home_state_reading
                        ReaderState.PAUSED -> R.string.home_state_paused
                        ReaderState.IDLE -> R.string.home_state_idle
                    },
                ),
            )
            val ttsText = when (ttsStatus) {
                TtsStatus.Initializing -> stringResource(R.string.home_tts_initializing)
                is TtsStatus.Ready -> stringResource(
                    if (ttsStatus.englishAvailable) R.string.home_tts_ready else R.string.home_tts_ready_no_english,
                )
                is TtsStatus.Error -> stringResource(R.string.home_tts_error, ttsStatus.message)
            }
            Text(
                text = ttsText,
                color = if (ttsStatus is TtsStatus.Error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
