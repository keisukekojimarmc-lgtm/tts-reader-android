package com.example.ttsreader.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.ttsreader.R
import com.example.ttsreader.overlay.FloatingOverlay
import com.example.ttsreader.reading.ReaderState
import com.example.ttsreader.reading.ReaderStateHolder
import com.example.ttsreader.reading.ReadingController
import com.example.ttsreader.reading.ScreenAccess
import com.example.ttsreader.reading.TtsStatus
import com.example.ttsreader.settings.ReaderPreferences
import com.example.ttsreader.tts.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前面アプリの画面テキスト取得・スクロール実行・TTS・フローティングボタンをまとめて所有するサービス。
 * 処理の中身は ReadingController / TtsEngine / FloatingOverlay に委譲する。
 */
class ReaderAccessibilityService :
    AccessibilityService(),
    ScreenAccess,
    TtsEngine.Callbacks,
    ReadingController.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: ReaderPreferences
    private lateinit var tts: TtsEngine
    private lateinit var controller: ReadingController
    private var overlay: FloatingOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "onServiceConnected")
        instance = this

        prefs = ReaderPreferences.get(this)
        tts = TtsEngine(this, this)
        controller = ReadingController(screen = this, tts = tts, listener = this)
        overlay = FloatingOverlay(
            context = this,
            prefs = prefs,
            onToggle = { toggleReading() },
        )

        ReaderStateHolder.setServiceConnected(true)
        ReaderStateHolder.setTtsStatus(TtsStatus.Initializing)

        scope.launch { prefs.speechRate.collect { tts.setSpeechRate(it) } }
        scope.launch { prefs.pitch.collect { tts.setPitch(it) } }
        scope.launch { prefs.overlayEnabled.collect { updateOverlayVisibility() } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (::controller.isInitialized) controller.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {
        if (::controller.isInitialized) controller.stop()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        tearDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    private fun tearDown() {
        if (instance !== this) return
        Log.i(TAG, "tearDown")
        instance = null
        scope.cancel()
        if (::controller.isInitialized) controller.stop()
        overlay?.hide()
        overlay = null
        if (::tts.isInitialized) tts.shutdown()
        ReaderStateHolder.setServiceConnected(false)
    }

    // ---- 外部（Activity / オーバーレイ）から呼ぶ操作 ----

    fun toggleReading() {
        if (!::controller.isInitialized) return
        if (!tts.isReady) {
            Toast.makeText(this, R.string.toast_tts_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        controller.toggle()
    }

    fun stopReading() {
        if (::controller.isInitialized) controller.stop()
    }

    /** 設定画面の「テスト再生」。読み上げ中は無視する。 */
    fun speakSample() {
        if (!::tts.isInitialized || !tts.isReady) {
            Toast.makeText(this, R.string.toast_tts_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        if (controller.isActive) return
        tts.speak(getString(R.string.home_test_sentence), SAMPLE_UTTERANCE_ID)
    }

    /** オーバーレイ設定・権限状態が変わった時に呼ぶ。 */
    fun updateOverlayVisibility() {
        val overlay = overlay ?: return
        val shouldShow = prefs.overlayEnabled.value && Settings.canDrawOverlays(this)
        if (shouldShow) overlay.show() else overlay.hide()
    }

    // ---- ScreenAccess ----

    override fun rootNode(): AccessibilityNodeInfo? = try {
        rootInActiveWindow
    } catch (e: Exception) {
        Log.w(TAG, "rootInActiveWindow failed", e)
        null
    }

    override fun screenRect(): Rect {
        val metrics = resources.displayMetrics
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    // ---- TtsEngine.Callbacks ----

    override fun onTtsReady() {
        ReaderStateHolder.setTtsStatus(TtsStatus.Ready)
    }

    override fun onTtsInitFailed(message: String) {
        ReaderStateHolder.setTtsStatus(TtsStatus.Error(message))
    }

    override fun onUtteranceDone(utteranceId: String) {
        if (::controller.isInitialized) controller.onUtteranceFinished(utteranceId)
    }

    override fun onUtteranceError(utteranceId: String) {
        // エラーでも次へ進めるよう、完了と同じ扱いにする
        if (::controller.isInitialized) controller.onUtteranceFinished(utteranceId)
    }

    // ---- ReadingController.Listener ----

    override fun onStateChanged(state: ReaderState) {
        ReaderStateHolder.setState(state)
    }

    override fun onNoTextFound() {
        Toast.makeText(this, R.string.toast_no_text, Toast.LENGTH_SHORT).show()
    }

    override fun finishedAnnouncement(): String = getString(R.string.tts_finished_announcement)

    companion object {
        private const val TAG = "ReaderA11yService"
        private const val SAMPLE_UTTERANCE_ID = "sample"

        /** 接続中のサービスインスタンス。未接続なら null。 */
        @Volatile
        var instance: ReaderAccessibilityService? = null
            private set
    }
}
