package com.example.ttsreader.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ユーザー設定。SharedPreferences に永続化しつつ、StateFlow で購読できるようにする。
 * Activity とサービスの両方から同じインスタンスを使う。
 */
class ReaderPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _speechRate = MutableStateFlow(prefs.getFloat(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _pitch = MutableStateFlow(prefs.getFloat(KEY_PITCH, DEFAULT_PITCH))
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _overlayEnabled = MutableStateFlow(prefs.getBoolean(KEY_OVERLAY_ENABLED, true))
    val overlayEnabled: StateFlow<Boolean> = _overlayEnabled.asStateFlow()

    private val _autoLanguage = MutableStateFlow(prefs.getBoolean(KEY_AUTO_LANGUAGE, true))

    /** 英語の文を英語音声で読むか（false なら常に日本語音声） */
    val autoLanguage: StateFlow<Boolean> = _autoLanguage.asStateFlow()

    fun setAutoLanguage(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LANGUAGE, enabled).apply()
        _autoLanguage.value = enabled
    }

    /** 速度を [delta] だけ増減する（0.1 刻みに丸める）。 */
    fun adjustSpeechRate(delta: Float) {
        val raw = _speechRate.value + delta
        val rounded = Math.round(raw * 10f) / 10f
        setSpeechRate(rounded)
    }

    fun setSpeechRate(rate: Float) {
        val value = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        prefs.edit().putFloat(KEY_SPEECH_RATE, value).apply()
        _speechRate.value = value
    }

    fun setPitch(pitch: Float) {
        val value = pitch.coerceIn(MIN_PITCH, MAX_PITCH)
        prefs.edit().putFloat(KEY_PITCH, value).apply()
        _pitch.value = value
    }

    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
        _overlayEnabled.value = enabled
    }

    /** フローティングボタンの位置（画面座標）。ドラッグで動かした位置を次回も使う。 */
    var overlayPosition: Pair<Int, Int>
        get() = prefs.getInt(KEY_OVERLAY_X, DEFAULT_OVERLAY_X) to prefs.getInt(KEY_OVERLAY_Y, DEFAULT_OVERLAY_Y)
        set(value) {
            prefs.edit().putInt(KEY_OVERLAY_X, value.first).putInt(KEY_OVERLAY_Y, value.second).apply()
        }

    companion object {
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2.0f
        const val DEFAULT_SPEECH_RATE = 1.0f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2.0f
        const val DEFAULT_PITCH = 1.0f

        private const val PREFS_NAME = "reader_prefs"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_PITCH = "pitch"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_AUTO_LANGUAGE = "auto_language"
        private const val KEY_OVERLAY_X = "overlay_x"
        private const val KEY_OVERLAY_Y = "overlay_y"
        private const val DEFAULT_OVERLAY_X = 24
        private const val DEFAULT_OVERLAY_Y = 400

        @Volatile
        private var instance: ReaderPreferences? = null

        fun get(context: Context): ReaderPreferences =
            instance ?: synchronized(this) {
                instance ?: ReaderPreferences(context.applicationContext).also { instance = it }
            }
    }
}
