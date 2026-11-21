package com.example.ttsreader.overlay

import androidx.annotation.DrawableRes
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ttsreader.R
import com.example.ttsreader.reading.ReaderState
import java.util.Locale

/**
 * フローティングコントローラー。
 *
 * 折りたたみ時: [再生/一時停止] [展開]
 * 展開時:       [再生/一時停止] [停止] [閉じる] [折りたたみ]
 *               [遅く] 1.0x [速く]
 *
 * 外側の Box がドラッグを、内側のボタンがタップを担当する。ドラッグ中はボタンの click が
 * キャンセルされるため、ドラッグ終了時に誤操作することはない。
 */
@Composable
fun FloatingController(
    state: ReaderState,
    speechRate: Float,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onAdjustRate: (delta: Float) -> Unit,
    onClose: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    val reading = state == ReaderState.READING

    Box(
        modifier = Modifier
            .padding(8.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = onPlayPause,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (reading) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            contentColor = if (reading) {
                                MaterialTheme.colorScheme.onTertiary
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            },
                        ),
                    ) {
                        Icon(
                            painter = painterResource(if (reading) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = stringResource(
                                when (state) {
                                    ReaderState.READING -> R.string.overlay_pause
                                    ReaderState.PAUSED -> R.string.overlay_resume
                                    ReaderState.IDLE -> R.string.overlay_play
                                },
                            ),
                        )
                    }
                    if (expanded) {
                        ControlButton(
                            iconRes = R.drawable.ic_stop,
                            contentDescription = stringResource(R.string.overlay_stop),
                            onClick = onStop,
                            enabled = state != ReaderState.IDLE,
                        )
                        ControlButton(
                            iconRes = R.drawable.ic_close,
                            contentDescription = stringResource(R.string.overlay_close),
                            onClick = onClose,
                        )
                    }
                    ControlButton(
                        iconRes = if (expanded) R.drawable.ic_chevron_left else R.drawable.ic_chevron_right,
                        contentDescription = stringResource(
                            if (expanded) R.string.overlay_collapse else R.string.overlay_expand,
                        ),
                        onClick = { expanded = !expanded },
                    )
                }
                if (expanded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        ControlButton(
                            iconRes = R.drawable.ic_minus,
                            contentDescription = stringResource(R.string.overlay_slower),
                            onClick = { onAdjustRate(-RATE_STEP) },
                        )
                        Text(
                            text = String.format(Locale.US, "%.1fx", speechRate),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = 44.dp),
                        )
                        ControlButton(
                            iconRes = R.drawable.ic_plus,
                            contentDescription = stringResource(R.string.overlay_faster),
                            onClick = { onAdjustRate(RATE_STEP) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painter = painterResource(iconRes), contentDescription = contentDescription)
    }
}

private const val RATE_STEP = 0.1f
