package com.example.ttsreader.reading

/** 読み上げに使う音声の言語。 */
enum class SpeechLanguage { JAPANESE, ENGLISH }

/**
 * 文字種から読み上げ言語を推定する。Android 非依存。
 *
 * - ひらがな・カタカナ・漢字などが 1 文字でも含まれていれば日本語
 * - 日本語文字が無く、ラテン文字が含まれていれば英語
 * - どちらも無い（数字や記号のみ）場合は「中立」= 判定不能
 */
object LanguageDetector {

    fun detect(text: String): SpeechLanguage = detectOrNull(text) ?: SpeechLanguage.JAPANESE

    /** 判定できない（数字・記号のみ）場合は null。 */
    fun detectOrNull(text: String): SpeechLanguage? {
        var latin = 0
        for (ch in text) {
            if (isJapanese(ch)) return SpeechLanguage.JAPANESE
            if (ch in 'A'..'Z' || ch in 'a'..'z') latin++
        }
        return if (latin > 0) SpeechLanguage.ENGLISH else null
    }

    private fun isJapanese(ch: Char): Boolean {
        val block = Character.UnicodeBlock.of(ch) ?: return false
        return block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
            block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
    }
}
