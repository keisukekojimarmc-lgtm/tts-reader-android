package com.example.ttsreader.reading

/**
 * 長いテキストを TTS に渡しやすい単位に分割する。Android 非依存。
 *
 * 1. 文末（。！？!? 改行）で文に分ける
 * 2. 短い文は [targetLength] を超えない範囲で結合し、発話回数を減らす
 * 3. それでも [maxLength]（TextToSpeech.getMaxSpeechInputLength 相当）を超える文は強制分割する
 */
object TextChunker {

    private val SENTENCE_BOUNDARY = Regex("(?<=[。！？!?\\n])")

    fun chunk(
        text: String,
        maxLength: Int = 4000,
        targetLength: Int = 300,
    ): List<String> {
        require(maxLength > 0) { "maxLength must be positive" }
        val effectiveTarget = targetLength.coerceIn(1, maxLength)

        val sentences = SENTENCE_BOUNDARY
            .split(text.replace("\r\n", "\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { hardSplit(it, maxLength) }

        val result = mutableListOf<String>()
        val buffer = StringBuilder()
        for (sentence in sentences) {
            if (buffer.isNotEmpty() && buffer.length + sentence.length > effectiveTarget) {
                result += buffer.toString()
                buffer.setLength(0)
            }
            buffer.append(sentence)
        }
        if (buffer.isNotEmpty()) result += buffer.toString()
        return result
    }

    private fun hardSplit(sentence: String, maxLength: Int): List<String> {
        if (sentence.length <= maxLength) return listOf(sentence)
        return sentence.chunked(maxLength)
    }
}
