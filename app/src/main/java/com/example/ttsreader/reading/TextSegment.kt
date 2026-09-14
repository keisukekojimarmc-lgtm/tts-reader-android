package com.example.ttsreader.reading

import android.graphics.Rect

/**
 * 画面から取得した 1 つのテキスト片。
 *
 * @param text      表示テキスト（text か contentDescription）
 * @param bounds    画面座標での表示位置
 * @param pageIndex 何回目のスクロール後に取得したか。スクロール後は bounds が古くなるため、
 *                  「画面下部に到達したか」の判定は現在ページのセグメントに対してのみ行う。
 */
data class TextSegment(
    val text: String,
    val bounds: Rect,
    val pageIndex: Int,
)
