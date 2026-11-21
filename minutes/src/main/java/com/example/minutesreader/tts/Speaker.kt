package com.example.minutesreader.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/** 読み上げプレイヤーの状態。index は文の番号、block はハイライトする段落の番号。 */
data class PlayerState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val index: Int = 0,
    val total: Int = 0,
    val block: Int = -1,
    val rate: Float = 1f,
)

/**
 * 1 文ずつ TextToSpeech に渡して読み上げる。
 * 1 文ずつにしているので、一時停止→再開、前後の文への移動、速度変更の即時反映ができる。
 */
class Speaker(context: Context) {
    private val app = context.applicationContext
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var items: List<SpeechItem> = emptyList()
    /** 古い発話の完了通知を無視するための世代番号 */
    @Volatile private var gen = 0
    @Volatile private var ended = false

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            val parts = utteranceId?.split(":") ?: return
            val g = parts.getOrNull(0)?.toIntOrNull() ?: return
            val idx = parts.getOrNull(1)?.toIntOrNull() ?: return
            if (g != gen) return
            val next = idx + 1
            if (next > items.lastIndex) {
                ended = true
                _state.update { it.copy(playing = false) }
            } else {
                _state.update { it.copy(index = next, block = items[next].block) }
                speakCurrent()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) { _state.update { it.copy(playing = false) } }
        override fun onError(utteranceId: String?, errorCode: Int) { _state.update { it.copy(playing = false) } }
    }

    private fun ensure() {
        if (tts != null) return
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.language = Locale.JAPANESE
                tts?.setOnUtteranceProgressListener(listener)
                if (_state.value.playing) speakCurrent()
            } else {
                _state.update { it.copy(playing = false) }
            }
        }
    }

    /** 読み上げる文を設定して start 番目から再生する。 */
    fun load(newItems: List<SpeechItem>, start: Int, rate: Float) {
        halt()
        items = newItems
        ended = false
        if (newItems.isEmpty()) { _state.value = PlayerState(rate = rate); return }
        val i = start.coerceIn(0, newItems.lastIndex)
        _state.value = PlayerState(active = true, playing = false, index = i, total = newItems.size, block = newItems[i].block, rate = rate)
        play()
    }

    fun play() {
        if (items.isEmpty()) return
        if (ended) {
            ended = false
            _state.update { it.copy(index = 0, block = items[0].block) }
        }
        _state.update { it.copy(playing = true) }
        ensure()
        if (ready) speakCurrent()
    }

    fun pause() {
        halt()
        _state.update { it.copy(playing = false) }
    }

    fun toggle() { if (_state.value.playing) pause() else play() }

    fun seek(index: Int) {
        if (items.isEmpty()) return
        val i = index.coerceIn(0, items.lastIndex)
        ended = false
        halt()
        _state.update { it.copy(index = i, block = items[i].block) }
        if (_state.value.playing && ready) speakCurrent()
    }

    fun next() = seek(_state.value.index + 1)

    fun prev() = seek(_state.value.index - 1)

    /** 段落の先頭の文へ移動する。 */
    fun seekBlock(block: Int) {
        val i = items.indexOfFirst { it.block == block }
        if (i >= 0) seek(i)
    }

    fun setRate(rate: Float) {
        _state.update { it.copy(rate = rate) }
        if (_state.value.playing && ready) {
            halt()
            speakCurrent()   // 今の文を新しい速さで読み直す
        }
    }

    /** 読み上げを終了してプレイヤーを閉じる。 */
    fun close() {
        halt()
        items = emptyList()
        ended = false
        _state.value = PlayerState(rate = _state.value.rate)
    }

    fun shutdown() {
        close()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun halt() {
        gen++
        tts?.stop()
    }

    private fun speakCurrent() {
        val t = tts ?: return
        val s = _state.value
        val item = items.getOrNull(s.index) ?: return
        t.setSpeechRate(s.rate)
        val g = ++gen
        t.speak(item.text, TextToSpeech.QUEUE_FLUSH, null, "$g:${s.index}")
    }
}
