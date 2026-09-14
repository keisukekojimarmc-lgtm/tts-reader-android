package com.example.ttsreader.reading

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 画面内で「前方向にスクロールできる」ノードを探して ACTION_SCROLL_FORWARD を実行する。
 * 面積の大きいノード（本文の RecyclerView / ScrollView / WebView など）から順に試す。
 */
object ScrollHelper {

    /**
     * @return いずれかのノードでスクロールに成功したら true。末尾に到達している等で全て失敗したら false。
     */
    fun scrollForward(root: AccessibilityNodeInfo?, screen: Rect): Boolean {
        if (root == null) return false
        val candidates = mutableListOf<Pair<AccessibilityNodeInfo, Int>>()
        collectScrollables(root, screen, candidates, depth = 0)
        candidates.sortByDescending { it.second }

        var scrolled = false
        for ((node, _) in candidates) {
            if (!scrolled && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                scrolled = true
            }
            recycleIfNeeded(node)
        }
        return scrolled
    }

    private fun collectScrollables(
        node: AccessibilityNodeInfo,
        screen: Rect,
        out: MutableList<Pair<AccessibilityNodeInfo, Int>>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH || !node.isVisibleToUser) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!Rect.intersects(bounds, screen)) return

        if (supportsScrollForward(node)) {
            val visible = Rect(bounds)
            visible.intersect(screen)
            // 候補として保持するので、ここでは recycle しない（コピーを取る）
            out += copyOf(node) to visible.width() * visible.height()
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectScrollables(child, screen, out, depth + 1)
            } finally {
                recycleIfNeeded(child)
            }
        }
    }

    private fun supportsScrollForward(node: AccessibilityNodeInfo): Boolean {
        if (!node.isScrollable) return false
        return node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }
    }

    @Suppress("DEPRECATION")
    private fun copyOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AccessibilityNodeInfo(node)
        } else {
            AccessibilityNodeInfo.obtain(node)
        }

    @Suppress("DEPRECATION")
    private fun recycleIfNeeded(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            node.recycle()
        }
    }

    private const val MAX_DEPTH = 64
}
