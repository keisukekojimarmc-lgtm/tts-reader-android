package com.example.ttsreader.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.ttsreader.R
import com.example.ttsreader.reading.SpeechLanguage
import java.util.Locale

/**
 * TextToSpeech の薄いラッパー。
 * - 日本語ロケールで初期化する
 * - 発話の完了/失敗を utteranceId 付きでメインスレッドに通知する
 * - 速度・ピッチは初期化前に設定されても、初期化完了時に再適用する
 */
class TtsEngine(context: Context, private val callbacks: Callbacks) : TextToSpeech.OnInitListener {

    interface Callbacks {
        /** @param englishAvailable 端末に英語音声があるか */
        fun onTtsReady(englishAvailable: Boolean)
        fun onTtsInitFailed(message: String)
        fun onUtteranceDone(utteranceId: String)
        fun onUtteranceError(utteranceId: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(appContext, this)

    @Volatile
    var isReady: Boolean = false
        private set

    private var pendingRate: Float = 1.0f
    private var pendingPitch: Float = 1.0f

    /** 端末に英語音声があるか。初期化完了時に判定する。 */
    @Volatile
    var englishAvailable: Boolean = false
        private set

    /** エンジンに現在設定している言語。speak() ごとに必要なら切り替える。 */
    private var currentLanguage: SpeechLanguage = SpeechLanguage.JAPANESE

    val maxInputLength: Int
        get() = TextToSpeech.getMaxSpeechInputLength()

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                utteranceId ?: return
                mainHandler.post { callbacks.onUtteranceDone(utteranceId) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId ?: return
                mainHandler.post { callbacks.onUtteranceError(utteranceId) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId ?: return
                Log.w(TAG, "TTS error code=$errorCode for $utteranceId")
                mainHandler.post { callbacks.onUtteranceError(utteranceId) }
            }
        })
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            notifyInitFailed(appContext.getString(R.string.tts_error_init_failed))
            return
        }
        when (tts.setLanguage(Locale.JAPANESE)) {
            TextToSpeech.LANG_MISSING_DATA ->
                notifyInitFailed(appContext.getString(R.string.tts_error_missing_data))
            TextToSpeech.LANG_NOT_SUPPORTED ->
                notifyInitFailed(appContext.getString(R.string.tts_error_not_supported))
            else -> {
                currentLanguage = SpeechLanguage.JAPANESE
                englishAvailable = tts.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE
                tts.setSpeechRate(pendingRate)
                tts.setPitch(pendingPitch)
                isReady = true
                val english = englishAvailable
                mainHandler.post { callbacks.onTtsReady(english) }
            }
        }
    }

    private fun notifyInitFailed(message: String) {
        isReady = false
        mainHandler.post { callbacks.onTtsInitFailed(message) }
    }

    fun setSpeechRate(rate: Float) {
        pendingRate = rate
        if (isReady) tts.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        pendingPitch = pitch
        if (isReady) tts.setPitch(pitch)
    }

    /**
     * キューの末尾に追加して発話する。成功なら true。
     * [language] が英語で端末に英語音声があれば en-US、それ以外は日本語で読む。
     * 言語は speak() 時点の設定がその発話に適用されるため、1 発話ずつ呼ぶ前提。
     */
    fun speak(
        text: String,
        utteranceId: String,
        language: SpeechLanguage = SpeechLanguage.JAPANESE,
    ): Boolean {
        if (!isReady) return false
        applyLanguage(language)
        val result = tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "speak() failed result=$result id=$utteranceId")
        }
        return result == TextToSpeech.SUCCESS
    }

    private fun applyLanguage(requested: SpeechLanguage) {
        val target = if (requested == SpeechLanguage.ENGLISH && englishAvailable) {
            SpeechLanguage.ENGLISH
        } else {
            SpeechLanguage.JAPANESE
        }
        if (target == currentLanguage) return
        val locale = if (target == SpeechLanguage.ENGLISH) Locale.US else Locale.JAPANESE
        val result = tts.setLanguage(locale)
        if (result >= TextToSpeech.LANG_AVAILABLE) {
            currentLanguage = target
        } else {
            Log.w(TAG, "setLanguage($locale) failed result=$result")
        }
    }

    fun stop() {
        if (isReady) tts.stop()
    }

    fun shutdown() {
        isReady = false
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val TAG = "TtsEngine"
    }
}
