package com.example.ttsreader.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ttsreader.reading.ReaderStateHolder
import com.example.ttsreader.settings.ReaderPreferences
import com.example.ttsreader.ui.theme.TtsReaderTheme
import kotlin.math.roundToInt

/**
 * SYSTEM_ALERT_WINDOW（TYPE_APPLICATION_OVERLAY）で表示するフローティングコントローラー。
 * Compose で描画し、ドラッグで位置を変えられる。
 */
class FloatingOverlay(
    private val context: Context,
    private val prefs: ReaderPreferences,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onPlayPause()
        fun onStop()
        fun onAdjustRate(delta: Float)
        fun onClose()
    }

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    val isShowing: Boolean get() = view != null

    fun show() {
        if (view != null) return
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "show() skipped: overlay permission not granted")
            return
        }

        val (startX, startY) = prefs.overlayPosition
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        val owner = OverlayLifecycleOwner().also { it.create() }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                TtsReaderTheme {
                    val state by ReaderStateHolder.state.collectAsStateWithLifecycle()
                    val speechRate by prefs.speechRate.collectAsStateWithLifecycle()
                    FloatingController(
                        state = state,
                        speechRate = speechRate,
                        onPlayPause = callbacks::onPlayPause,
                        onStop = callbacks::onStop,
                        onAdjustRate = callbacks::onAdjustRate,
                        onClose = callbacks::onClose,
                        onDrag = { dx, dy -> moveBy(dx, dy) },
                    )
                }
            }
            // 展開/折りたたみでサイズが変わった時に画面外へはみ出さないよう位置を補正する
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val sizeChanged = (right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)
                if (sizeChanged) post { moveBy(0f, 0f) }
            }
        }

        try {
            windowManager.addView(composeView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
            owner.destroy()
            return
        }
        owner.resume()

        view = composeView
        params = layoutParams
        lifecycleOwner = owner
    }

    fun hide() {
        val current = view ?: return
        try {
            windowManager.removeViewImmediate(current)
        } catch (e: Exception) {
            Log.w(TAG, "removeView failed", e)
        }
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        view = null
        params = null
    }

    private fun moveBy(dx: Float, dy: Float) {
        val current = view ?: return
        val layoutParams = params ?: return
        val metrics = context.resources.displayMetrics
        val maxX = (metrics.widthPixels - current.width).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - current.height).coerceAtLeast(0)
        layoutParams.x = (layoutParams.x + dx).roundToInt().coerceIn(0, maxX)
        layoutParams.y = (layoutParams.y + dy).roundToInt().coerceIn(0, maxY)
        try {
            windowManager.updateViewLayout(current, layoutParams)
        } catch (e: Exception) {
            Log.w(TAG, "updateViewLayout failed", e)
            return
        }
        prefs.overlayPosition = layoutParams.x to layoutParams.y
    }

    private companion object {
        const val TAG = "FloatingOverlay"
    }
}
