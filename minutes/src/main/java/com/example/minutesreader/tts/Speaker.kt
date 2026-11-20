package com.example.minutesreader.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** 議事録の読み上げ（端末の TTS エンジンを使用）。 */
class Speaker(context: Context) {
    private val app = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<List<String>, Float>? = null

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) { if (utteranceId == LAST) _speaking.value = false }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) { _speaking.value = false }
        override fun onError(utteranceId: String?, errorCode: Int) { _speaking.value = false }
    }

    private fun ensure() {
        if (tts != null) return
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.language = Locale.JAPANESE
                tts?.setOnUtteranceProgressListener(listener)
                pending?.let { (c, r) -> pending = null; play(c, r) }
            } else {
                pending = null
                _speaking.value = false
            }
        }
    }

    fun speak(text: String, rate: Float) {
        val chunks = SpeechText.chunks(text)
        if (chunks.isEmpty()) return
        _speaking.value = true
        ensure()
        if (ready) play(chunks, rate) else pending = chunks to rate
    }

    private fun play(chunks: List<String>, rate: Float) {
        val t = tts ?: return
        t.setSpeechRate(rate)
        chunks.forEachIndexed { i, c ->
            t.speak(c, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == chunks.lastIndex) LAST else "u$i")
        }
    }

    fun stop() {
        pending = null
        tts?.stop()
        _speaking.value = false
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }

    private companion object { const val LAST = "last" }
}
