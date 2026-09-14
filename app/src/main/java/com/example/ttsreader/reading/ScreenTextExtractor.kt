package com.example.ttsreader.reading

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AccessibilityNodeInfo ツリーを深さ優先で走査し、画面内に見えているテキストを
 * レイアウト順（≒上から下）に取り出す。
 */
class ScreenTextExtractor {

    fun extract(root: AccessibilityNodeInfo?, screen: Rect, pageIndex: Int): List<TextSegment> {
        val result = mutableListOf<TextSegment>()
        if (root == null) return result
        walk(root, screen, pageIndex, result, depth = 0)
        return result
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        screen: Rect,
        pageIndex: Int,
        out: MutableList<TextSegment>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH) return
        if (!node.isVisibleToUser) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty || !Rect.intersects(bounds, screen)) return

        pickText(node)?.let { out += TextSegment(it, Rect(bounds), pageIndex) }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            try {
                walk(child, screen, pageIndex, out, depth + 1)
            } finally {
                recycleIfNeeded(child)
            }
        }
    }

    /** text を優先し、無ければ contentDescription を使う。パスワードやヒント表示中の入力欄は読まない。 */
    private fun pickText(node: AccessibilityNodeInfo): String? {
        if (node.isPassword) return null
        if (node.isEditable && node.isShowingHintText) return null

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) return text

        val description = node.contentDescription?.toString()?.trim()
        if (!description.isNullOrEmpty()) return description
        return null
    }

    @Suppress("DEPRECATION")
    private fun recycleIfNeeded(node: AccessibilityNodeInfo) {
        // API 33 以降は recycle() が no-op になった（非推奨）。それ以前ではプールに返す。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            node.recycle()
        }
    }

    private companion object {
        const val MAX_DEPTH = 64
    }
}
