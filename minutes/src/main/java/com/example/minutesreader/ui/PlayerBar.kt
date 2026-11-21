package com.example.minutesreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.minutesreader.tts.PlayerState
import kotlin.math.roundToInt

/** 読み上げ中に画面下に出る操作パネル。 */
@Composable
fun PlayerBar(
    st: PlayerState,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onRate: (Float) -> Unit,
    onClose: () -> Unit,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val max = (st.total - 1).coerceAtLeast(1)
    Surface(tonalElevation = 3.dp, shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 6.dp),
        ) {
            // 1 段目: 位置（文の番号）とシークバー
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${((drag?.roundToInt() ?: st.index) + 1)} / ${st.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = (drag ?: st.index.toFloat()).coerceIn(0f, max.toFloat()),
                    onValueChange = { drag = it },
                    onValueChangeFinished = {
                        drag?.let { onSeek(it.roundToInt()) }
                        drag = null
                    },
                    valueRange = 0f..max.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "読み上げを終了") }
            }
            // 2 段目: 速さ / 前の文・再生・次の文
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { onRate(st.rate - 0.1f) }, enabled = st.rate > 0.55f) {
                    Icon(PlayerIcons.Remove, "遅く")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f倍".format(st.rate), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("速さ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onRate(st.rate + 0.1f) }, enabled = st.rate < 2.95f) {
                    Icon(Icons.Default.Add, "速く")
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrev, enabled = st.index > 0) { Icon(PlayerIcons.SkipPrevious, "前の文") }
                    FilledIconButton(onClick = onToggle, modifier = Modifier.size(52.dp)) {
                        if (st.playing) Icon(PlayerIcons.Pause, "一時停止") else Icon(Icons.Default.PlayArrow, "再生")
                    }
                    IconButton(onClick = onNext, enabled = st.index < st.total - 1) { Icon(PlayerIcons.SkipNext, "次の文") }
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
