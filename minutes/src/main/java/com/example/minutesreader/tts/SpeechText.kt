package com.example.minutesreader.tts

/** 読み上げ 1 単位（1 文）。block は本文中の段落・行の番号（ハイライト用）。 */
data class SpeechItem(val text: String, val block: Int)

/** 読み上げ用テキストの整形と分割（純粋関数）。 */
object SpeechText {
    private val TIMESTAMP = Regex("""\[\d{1,2}:\d{2}[^\]\n]*]""")
    private val SENTENCE_END = Regex("""(?<=[。！？!?])""")

    fun clean(s: String): String =
        s.replace(TIMESTAMP, "")
            .replace("|", "、")
            .replace(Regex("""https?://\S+"""), "")
            .replace(Regex("""[#*_`>~]+"""), "")
            .trim()

    /** 1 つの段落を文ごとに分ける。長すぎる文は max 文字で区切る。 */
    fun sentences(text: String, max: Int = 300): List<String> {
        val out = ArrayList<String>()
        for (raw in clean(text).split(SENTENCE_END)) {
            var s = raw.trim().trim('、').trim()
            if (s.isEmpty() || s.all { it == '-' || it == '、' || it.isWhitespace() }) continue
            while (s.length > max) {
                out += s.take(max)
                s = s.drop(max)
            }
            if (s.isNotEmpty()) out += s
        }
        return out
    }

    /** 本文の段落リストを、読み上げ単位（文）のリストにする。 */
    fun items(blocks: List<String>): List<SpeechItem> =
        blocks.flatMapIndexed { b, t -> sentences(t).map { SpeechItem(it, b) } }
}
