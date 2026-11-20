package com.example.minutesreader.tts

/** 読み上げ用テキストの整形と分割（純粋関数）。 */
object SpeechText {
    private val TIMESTAMP = Regex("""\[\d{1,2}:\d{2}[^\]\n]*]""")

    fun clean(s: String): String =
        s.replace(TIMESTAMP, "")
            .replace("|", "、")
            .replace(Regex("""https?://\S+"""), "")
            .replace(Regex("""[#*_`>~]+"""), "")

    /** TextToSpeech の 1 回あたりの上限を超えないよう、行→文の順に分割する。 */
    fun chunks(text: String, max: Int = 1000): List<String> {
        val out = ArrayList<String>()
        for (line in clean(text).lines()) {
            val l = line.trim().trim('、').trim()
            if (l.isEmpty() || l.all { it == '-' || it == '、' }) continue
            if (l.length <= max) { out += l; continue }
            val sb = StringBuilder()
            for (sentence in l.split(Regex("(?<=。)"))) {
                if (sb.length + sentence.length > max && sb.isNotEmpty()) { out += sb.toString(); sb.clear() }
                var rest = sentence
                while (rest.length > max) { out += rest.take(max); rest = rest.drop(max) }
                sb.append(rest)
            }
            if (sb.isNotEmpty()) out += sb.toString()
        }
        return out
    }
}
