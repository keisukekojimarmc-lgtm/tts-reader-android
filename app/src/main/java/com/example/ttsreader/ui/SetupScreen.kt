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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ttsreader.R

/**
 * 初回起動時（または権限が欠けている時）に表示する案内画面。
 * ユーザー補助とオーバーレイの 2 権限について、状態と設定画面への導線を出す。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    accessibilityGranted: Boolean,
    overlayGranted: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_intro),
                style = MaterialTheme.typography.bodyLarge,
            )

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

            Text(
                text = stringResource(R.string.setup_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                StatusChip(granted = granted)
            }
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            if (!granted) {
                Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text(buttonLabel)
                }
            }
        }
    }
}

@Composable
fun StatusChip(granted: Boolean) {
    Text(
        text = stringResource(if (granted) R.string.setup_granted else R.string.setup_not_granted),
        style = MaterialTheme.typography.labelLarge,
        color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold,
    )
}
