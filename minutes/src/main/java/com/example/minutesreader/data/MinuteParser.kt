package com.example.minutesreader.data

import com.example.minutesreader.dropbox.RemoteFile
import java.time.LocalDate

/**
 * ファイル名・フロントマター（--- で囲まれた YAML 風のメタ情報）・概要の抽出。
 * Android に依存しない純粋な Kotlin なので単体テストできる。
 */
object MinuteParser {

    data class NameInfo(val date: String?, val time: String?, val category: String, val title: String)

    data class FrontMatter(val fields: Map<String, String>, val body: String)

    // 例: 2026-11-18_1500_CCBJ_ABP_ファイナンスレビュー
    private val FILE_RE = Regex("""^(\d{4}-\d{2}-\d{2})(?:_(\d{4}))?(?:_(.*))?$""")
    private val KEY_RE = Regex("""^([A-Za-z0-9_\-]+):(?:\s+(.*))?$""")
    private val DATE_RE = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val TIME_RE = Regex("""^(\d{1,2}):(\d{2})""")
    private val SUMMARY_HEADING_RE = Regex("""^#{2,3}\s*(概要|サマリー|サマリ|要約|まとめ|Summary)""", RegexOption.IGNORE_CASE)
    private val CLOSING_RE = Regex("""^---\s*$""", RegexOption.MULTILINE)

    fun parseFileName(fileName: String): NameInfo {
        val base = if (fileName.endsWith(".md", ignoreCase = true)) fileName.dropLast(3) else fileName
        val m = FILE_RE.matchEntire(base) ?: return NameInfo(null, null, "", base.replace('_', ' ').trim())
        val date = m.groupValues[1]
        val time = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { "${it.substring(0, 2)}:${it.substring(2)}" }
        val rest = m.groupValues[3].split('_').filter { it.isNotBlank() }
        val category = if (rest.size >= 2) rest[0] else ""
        val title = (if (rest.size >= 2) rest.drop(1) else rest).joinToString(" ").ifEmpty { date }
        return NameInfo(date, time, category, title)
    }

    fun splitFrontMatter(raw: String): FrontMatter {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n")
        if (!text.startsWith("---\n")) return FrontMatter(emptyMap(), text)
        val end = CLOSING_RE.find(text, 4) ?: return FrontMatter(emptyMap(), text)
        val block = text.substring(4, end.range.first)
        val body = if (end.range.last + 1 >= text.length) "" else text.substring(end.range.last + 1).trimStart('\n')

        val fields = LinkedHashMap<String, String>()
        var lastKey: String? = null
        for (line in block.lines()) {
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val km = if (line.first().isWhitespace()) null else KEY_RE.matchEntire(line)
            if (km != null) {
                val key = km.groupValues[1]
                fields[key] = unquote(km.groupValues[2].trim())
                lastKey = key
            } else if (lastKey != null) {
                // 「- 値」形式のリストや折り返し行は直前のキーにつなげる
                val v = unquote(line.trim().removePrefix("-").trim())
                if (v.isEmpty()) continue
                val prev = fields[lastKey].orEmpty()
                fields[lastKey] = if (prev.isEmpty()) v else "$prev、$v"
            }
        }
        return FrontMatter(fields, body)
    }

    fun extractSummary(body: String, max: Int = 140): String {
        val lines = body.lines()
        val idx = lines.indexOfFirst { SUMMARY_HEADING_RE.containsMatchIn(it) }
        val segment = if (idx >= 0) {
            lines.drop(idx + 1).takeWhile { !it.startsWith("#") }
        } else {
            lines.dropWhile { it.isBlank() || it.startsWith("#") || it.startsWith(">") }
                .takeWhile { it.isNotBlank() }
        }
        val text = segment.map(::cleanInline).filter { it.isNotBlank() }.joinToString(" ")
        return if (text.length > max) text.take(max).trimEnd() + "…" else text
    }

    fun build(file: RemoteFile, content: String?): Minute {
        val n = parseFileName(file.name)
        val fm = content?.let(::splitFrontMatter)
        val f = fm?.fields.orEmpty()
        val date = n.date ?: f["date"]?.takeIf { DATE_RE.matches(it) }
        val time = n.time ?: f["time"]?.let { TIME_RE.find(it) }?.let {
            it.groupValues[1].padStart(2, '0') + ":" + it.groupValues[2]
        }
        return Minute(
            id = file.id,
            path = file.pathDisplay,
            name = file.name,
            rev = file.rev,
            serverModified = file.serverModified,
            date = date,
            time = time,
            category = f["category"]?.takeIf { it.isNotBlank() } ?: n.category,
            title = f["title"]?.takeIf { it.isNotBlank() } ?: n.title,
            summary = fm?.let { extractSummary(it.body) }.orEmpty(),
            participants = f["participants"].orEmpty(),
        )
    }

    /** 新しい日付が上、日付なしの資料は最後。 */
    val ORDER: Comparator<Minute> = Comparator { a, b ->
        when {
            a.date != null && b.date == null -> -1
            a.date == null && b.date != null -> 1
            a.date != null && b.date != null && a.date != b.date -> b.date.compareTo(a.date)
            a.date != null && (a.time ?: "") != (b.time ?: "") -> (b.time ?: "").compareTo(a.time ?: "")
            a.date != null -> b.name.compareTo(a.name)
            else -> a.name.compareTo(b.name)
        }
    }

    private val WEEK = arrayOf("月", "火", "水", "木", "金", "土", "日")

    fun weekday(date: String): String =
        runCatching { WEEK[LocalDate.parse(date).dayOfWeek.value - 1] }.getOrDefault("")

    /** 一覧用: 11/18(水) 15:00 */
    fun shortLabel(m: Minute): String {
        val d = m.date ?: return "資料"
        val md = "${d.substring(5, 7).toInt()}/${d.substring(8, 10).toInt()}"
        val w = weekday(d).let { if (it.isEmpty()) "" else "($it)" }
        return listOfNotNull("$md$w", m.time).joinToString(" ")
    }

    /** 本文ヘッダー用: 2026年11月18日(水) */
    fun longDate(d: String): String {
        if (!DATE_RE.matches(d)) return d
        val w = weekday(d).let { if (it.isEmpty()) "" else "($it)" }
        return "${d.substring(0, 4)}年${d.substring(5, 7).toInt()}月${d.substring(8, 10).toInt()}日$w"
    }

    fun monthKey(m: Minute): String =
        m.date?.let { "${it.substring(0, 4)}年${it.substring(5, 7).toInt()}月" } ?: "資料・その他"

    private fun unquote(s: String): String =
        if (s.length >= 2 && ((s.first() == '"' && s.last() == '"') || (s.first() == '\'' && s.last() == '\''))) {
            s.substring(1, s.length - 1)
        } else {
            s
        }

    private fun cleanInline(s: String): String =
        s.trim().removePrefix(">").trim()
            .replace(Regex("""^([-*+]|\d+\.)\s+"""), "")
            .replace("**", "").replace("__", "").replace("`", "")
            .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
            .replace(Regex("""^\|.*\|$"""), "")
}
