package com.example.ttsreader.overlay

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ttsreader.R
import com.example.ttsreader.reading.ReaderState

/**
 * フローティングの再生/停止ボタン。
 * 外側の Box がドラッグを、内側の FAB がタップを担当する。ドラッグ中は FAB の click が
 * キャンセルされるため、ドラッグ終了時に誤って再生/停止が切り替わることはない。
 */
@Composable
fun FloatingButton(
    state: ReaderState,
    onClick: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
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
        FloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = if (reading) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            contentColor = if (reading) {
                MaterialTheme.colorScheme.onError
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
        ) {
            Icon(
                painter = painterResource(if (reading) R.drawable.ic_stop else R.drawable.ic_play),
                contentDescription = stringResource(
                    if (reading) R.string.overlay_stop else R.string.overlay_play,
                ),
            )
        }
    }
}
