package com.example.ttsreader.reading

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.ttsreader.tts.TtsEngine

/**
 * 読み上げと自動スクロールを司る状態機械。全メソッドはメインスレッドから呼ぶこと。
 *
 * 流れ:
 *  1. start(): 画面の可視テキストを取得 → 未読分だけキューへ → 順に発話
 *  2. 発話中の要素が画面下部（[SCROLL_TRIGGER_RATIO]）に達したら、先読みのために ACTION_SCROLL_FORWARD
 *  3. スクロール完了（TYPE_VIEW_SCROLLED または タイムアウト）後に再取得し、履歴に無いテキストだけ追加
 *  4. キューが空になった時点で未スクロールなら再度スクロール。スクロールできない／新規テキストが
 *     続けて見つからない場合は終了を告げて停止
 *
 * 一時停止: TextToSpeech に pause が無いため、発話を止めて現在の発話をキュー先頭に戻し、
 * 再開時にその発話の先頭から読み直す。
 */
class ReadingController(
    private val screen: ScreenAccess,
    private val tts: TtsEngine,
    private val listener: Listener,
) {

    interface Listener {
        fun onStateChanged(state: ReaderState)
        fun onNoTextFound()
        fun finishedAnnouncement(): String
    }

    private class Utterance(
        val id: String,
        val text: String,
        val language: SpeechLanguage,
        val segment: TextSegment,
    )

    private val handler = Handler(Looper.getMainLooper())
    private val extractor = ScreenTextExtractor()
    private val history = ReadHistory()
    private val queue = ArrayDeque<Utterance>()

    private var active = false
    private var paused = false
    private var current: Utterance? = null
    private var pageIndex = 0
    private var utteranceCounter = 0

    private var scrollPending = false
    private var scrollRequestedAt = 0L
    private var emptyScrollStreak = 0
    private var endReached = false
    private var announcingEnd = false

    private val settleRunnable = Runnable { onScrollSettled() }

    /** 英語の文を英語音声で読むか。false なら全て日本語音声。 */
    @Volatile
    var autoLanguage: Boolean = true

    val isActive: Boolean get() = active
    val isPaused: Boolean get() = active && paused

    val state: ReaderState
        get() = when {
            !active -> ReaderState.IDLE
            paused -> ReaderState.PAUSED
            else -> ReaderState.READING
        }

    /** 停止中なら開始、再生中なら停止。 */
    fun toggle() {
        if (active) stop() else start()
    }

    /** 停止中なら開始、再生中なら一時停止、一時停止中なら再開。 */
    fun togglePlayPause() {
        when {
            !active -> start()
            paused -> resume()
            else -> pause()
        }
    }

    fun start() {
        if (active) return
        if (!tts.isReady) {
            Log.w(TAG, "start() ignored: TTS not ready")
            return
        }
        active = true
        resetSession()
        listener.onStateChanged(ReaderState.READING)

        val added = collectVisibleText()
        if (added == 0) {
            // 画面に読めるものが無い。まずは 1 回スクロールして探す。
            requestScroll()
        } else {
            speakNext()
        }
    }

    fun pause() {
        if (!active || paused || announcingEnd) return
        paused = true
        val speaking = current
        current = null
        if (speaking != null) queue.addFirst(speaking)
        tts.stop()
        listener.onStateChanged(ReaderState.PAUSED)
    }

    fun resume() {
        if (!active || !paused) return
        paused = false
        listener.onStateChanged(ReaderState.READING)
        speakNext()
    }

    fun stop() {
        if (!active) return
        active = false
        paused = false
        handler.removeCallbacks(settleRunnable)
        tts.stop()
        queue.clear()
        current = null
        scrollPending = false
        announcingEnd = false
        listener.onStateChanged(ReaderState.IDLE)
    }

    /** AccessibilityService から転送されるイベント。 */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!active || !scrollPending) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            // スクロールアニメーション中はイベントが連続するので、最後のイベントから少し待つ。
            // ただし全体で SCROLL_SETTLE_MAX_MS を超えないよう上限を設ける。
            val elapsed = SystemClock.uptimeMillis() - scrollRequestedAt
            val delay = SCROLL_SETTLE_AFTER_EVENT_MS.coerceAtMost((SCROLL_SETTLE_MAX_MS - elapsed).coerceAtLeast(0L))
            scheduleSettle(delay)
        }
    }

    /** TtsEngine からの完了通知（メインスレッド）。 */
    fun onUtteranceFinished(utteranceId: String) {
        if (!active) return
        if (announcingEnd && utteranceId == END_UTTERANCE_ID) {
            finish()
            return
        }
        val speaking = current ?: return
        if (speaking.id != utteranceId) return
        current = null
        speakNext()
    }

    // ---- 内部処理 ----

    private fun resetSession() {
        history.clear()
        queue.clear()
        current = null
        paused = false
        pageIndex = 0
        scrollPending = false
        emptyScrollStreak = 0
        endReached = false
        announcingEnd = false
        handler.removeCallbacks(settleRunnable)
    }

    /**
     * 現在の画面から可視テキストを取得し、履歴に無いものだけをキューへ追加する。
     * @return 追加した発話数
     */
    private fun collectVisibleText(): Int {
        val root = screen.rootNode() ?: return 0
        val segments = extractor.extract(root, screen.screenRect(), pageIndex)
        var added = 0
        for (segment in segments) {
            val key = TextNormalizer.normalize(segment.text)
            if (!history.add(key)) continue
            for (chunk in TextChunker.chunkWithLanguage(segment.text, tts.maxInputLength)) {
                val language = if (autoLanguage) chunk.language else SpeechLanguage.JAPANESE
                queue.addLast(Utterance(nextUtteranceId(), chunk.text, language, segment))
                added++
            }
        }
        Log.d(TAG, "collectVisibleText page=$pageIndex segments=${segments.size} added=$added")
        return added
    }

    private fun nextUtteranceId(): String = "u-${++utteranceCounter}"

    private fun speakNext() {
        if (!active || paused || current != null || announcingEnd) return
        val next = queue.removeFirstOrNull()
        if (next == null) {
            when {
                endReached -> announceEndAndFinish()
                !scrollPending -> requestScroll()
                // スクロール結果待ち。settle 後に speakNext() が再度呼ばれる。
            }
            return
        }
        current = next
        if (!tts.speak(next.text, next.id, next.language)) {
            // 発話に失敗したら飛ばして次へ
            current = null
            handler.post { speakNext() }
            return
        }
        maybeScrollAhead(next)
    }

    /** 発話中の要素が画面下部に達していたら、先読みのためにスクロールする。 */
    private fun maybeScrollAhead(utterance: Utterance) {
        if (scrollPending || endReached) return
        if (utterance.segment.pageIndex != pageIndex) return // スクロール後は座標が古い
        val screenRect = screen.screenRect()
        val threshold = screenRect.top + screenRect.height() * SCROLL_TRIGGER_RATIO
        if (utterance.segment.bounds.bottom >= threshold) {
            requestScroll()
        }
    }

    private fun requestScroll() {
        if (!active || paused || scrollPending || endReached) return
        val scrolled = ScrollHelper.scrollForward(screen.rootNode(), screen.screenRect())
        Log.d(TAG, "requestScroll -> $scrolled")
        if (!scrolled) {
            onEndReached()
            return
        }
        scrollPending = true
        scrollRequestedAt = SystemClock.uptimeMillis()
        scheduleSettle(SCROLL_SETTLE_FALLBACK_MS)
    }

    private fun scheduleSettle(delayMs: Long) {
        handler.removeCallbacks(settleRunnable)
        handler.postDelayed(settleRunnable, delayMs)
    }

    private fun onScrollSettled() {
        if (!active || !scrollPending) return
        scrollPending = false
        pageIndex++

        val added = collectVisibleText()
        if (added > 0) {
            emptyScrollStreak = 0
            if (current == null) speakNext()
            return
        }

        emptyScrollStreak++
        if (emptyScrollStreak >= MAX_EMPTY_SCROLLS) {
            onEndReached()
        } else if (current == null && !paused) {
            // 何も話していないなら、続けてもう一度スクロールして探す
            requestScroll()
        }
        // 発話中なら、その完了時の speakNext() → requestScroll() で再試行される
    }

    private fun onEndReached() {
        endReached = true
        scrollPending = false
        handler.removeCallbacks(settleRunnable)
        if (current == null && queue.isEmpty() && !paused) {
            announceEndAndFinish()
        }
        // まだ話す内容が残っていれば、それを話し切ってから speakNext() 経由で終了する
    }

    private fun announceEndAndFinish() {
        if (!active || announcingEnd) return
        if (history.size == 0) {
            // 一度も読めなかった
            listener.onNoTextFound()
            finish()
            return
        }
        announcingEnd = true
        if (!tts.speak(listener.finishedAnnouncement(), END_UTTERANCE_ID, SpeechLanguage.JAPANESE)) {
            finish()
        }
    }

    private fun finish() {
        active = false
        paused = false
        announcingEnd = false
        handler.removeCallbacks(settleRunnable)
        queue.clear()
        current = null
        scrollPending = false
        listener.onStateChanged(ReaderState.IDLE)
    }

    companion object {
        private const val TAG = "ReadingController"

        /** 発話中の要素の下端が画面高さのこの割合を超えたらスクロールする */
        const val SCROLL_TRIGGER_RATIO = 0.75f

        /** TYPE_VIEW_SCROLLED が来ない場合にスクロール完了とみなすまでの時間 */
        const val SCROLL_SETTLE_FALLBACK_MS = 800L

        /** 最後の TYPE_VIEW_SCROLLED からこの時間待って再取得する */
        const val SCROLL_SETTLE_AFTER_EVENT_MS = 350L

        /** スクロール要求からの最大待ち時間 */
        const val SCROLL_SETTLE_MAX_MS = 2000L

        /** スクロールしても新規テキストが見つからない回数がこれに達したら終了 */
        const val MAX_EMPTY_SCROLLS = 3

        const val END_UTTERANCE_ID = "end-of-content"
    }
}
