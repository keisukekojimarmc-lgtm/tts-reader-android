package com.example.ttsreader.reading

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * ReadingController が AccessibilityService から必要とする最小限の窓口。
 * サービス本体への依存を切り離し、差し替え・テストをしやすくする。
 */
interface ScreenAccess {
    /** 前面ウィンドウのルートノード。取得できなければ null。 */
    fun rootNode(): AccessibilityNodeInfo?

    /** 画面全体の矩形（画面座標）。 */
    fun screenRect(): Rect
}
