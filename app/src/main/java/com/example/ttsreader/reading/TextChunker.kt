package com.example.ttsreader.reading

/** TTS に渡す 1 発話分のテキストと、その言語。 */
data class SpeechChunk(val text: String, val language: SpeechLanguage)

/**
 * 長いテキストを TTS に渡しやすい単位に分割する。Android 非依存。
 *
 * 1. 文末（。！？!? 改行、および空白が続くピリオド）で文に分ける
 * 2. 文ごとに言語を推定し、同じ言語の短い文は [targetLength] を超えない範囲で結合する
 *    （言語が変わるところでは必ず区切る。数字・記号だけの文は直前の言語を引き継ぐ）
 * 3. それでも [maxLength]（TextToSpeech.getMaxSpeechInputLength 相当）を超える文は強制分割する
 *
 * 一時停止からの再開は発話単位の先頭に戻るため、[targetLength] は短めにしてある。
 */
object TextChunker {

    /**
     * 文末の直後で区切る。英文のピリオドは「直後が空白か末尾」の場合のみ文末とみなし、
     * 小数点（3.14）や URL の途中では区切らない。
     */
    private val SENTENCE_BOUNDARY = Regex("(?<=[。！？!?\\n])|(?<=\\.)(?=\\s|$)")

    const val DEFAULT_TARGET_LENGTH = 200

    fun chunk(
        text: String,
        maxLength: Int = 4000,
        targetLength: Int = DEFAULT_TARGET_LENGTH,
    ): List<String> = chunkWithLanguage(text, maxLength, targetLength).map { it.text }

    fun chunkWithLanguage(
        text: String,
        maxLength: Int = 4000,
        targetLength: Int = DEFAULT_TARGET_LENGTH,
    ): List<SpeechChunk> {
        require(maxLength > 0) { "maxLength must be positive" }
        val effectiveTarget = targetLength.coerceIn(1, maxLength)

        val sentences = SENTENCE_BOUNDARY
            .split(text.replace("\r\n", "\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { hardSplit(it, maxLength) }

        val result = mutableListOf<SpeechChunk>()
        val buffer = StringBuilder()
        var bufferLanguage: SpeechLanguage? = null

        fun flush() {
            if (buffer.isNotEmpty()) {
                result += SpeechChunk(buffer.toString(), bufferLanguage ?: SpeechLanguage.JAPANESE)
                buffer.setLength(0)
            }
        }

        for (sentence in sentences) {
            val language = LanguageDetector.detectOrNull(sentence) ?: bufferLanguage
            val tooLong = buffer.length + sentence.length > effectiveTarget
            val languageChanged = language != null && bufferLanguage != null && language != bufferLanguage
            if (buffer.isNotEmpty() && (tooLong || languageChanged)) flush()
            // 英文同士は空白で区切って結合する（日本語は詰める）
            if (buffer.isNotEmpty() && (language ?: bufferLanguage) == SpeechLanguage.ENGLISH) buffer.append(' ')
            buffer.append(sentence)
            if (language != null) bufferLanguage = language
        }
        flush()
        return result
    }

    private fun hardSplit(sentence: String, maxLength: Int): List<String> {
        if (sentence.length <= maxLength) return listOf(sentence)
        return sentence.chunked(maxLength)
    }
}
