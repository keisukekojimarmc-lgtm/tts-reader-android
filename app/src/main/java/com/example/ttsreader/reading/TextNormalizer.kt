package com.example.ttsreader.reading

/** 重複判定用にテキストを正規化する（空白の揺れを吸収）。Android 非依存。 */
object TextNormalizer {
    private val WHITESPACE = Regex("\\s+")

    fun normalize(text: String): String = text.replace(WHITESPACE, " ").trim()
}
