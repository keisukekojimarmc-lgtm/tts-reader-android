package com.example.minutesreader

import com.example.minutesreader.render.MinutesRenderer
import com.example.minutesreader.tts.SpeechText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererTest {
    private val md = """
---
date: 2026-11-18
category: CCBJ
participants: 小島
notion: https://example.com/p
---

# タイトル

## 概要

本文 <script>alert(1)</script>

## ToDo

| 担当 | 内容 | 期限 |
|---|---|---|
| 小島 | 調整 | 11/19 |

### 1. 論点 [00:00〜06:00]

- 人数（要確認）
""".trimStart()

    @Test fun rendersHeaderTablesAndToc() {
        val doc = MinutesRenderer.render(md, "fallback", dark = false)
        assertTrue(doc.html.contains("<table>"))
        assertTrue(doc.html.contains("👥 小島"))
        assertTrue(doc.html.contains("2026年11月18日(水)"))
        assertTrue(doc.html.contains("<a href=\"https://example.com/p\">"))
        assertFalse("raw HTML must be escaped", doc.html.contains("<script>alert"))
        assertEquals(listOf("概要", "ToDo", "1. 論点 [00:00〜06:00]"), doc.toc.map { it.text })
        assertEquals(listOf(2, 2, 3), doc.toc.map { it.level })
    }

    @Test fun sentencesRemoveTimestampsAndSplit() {
        assertEquals(listOf("1. 論点"), SpeechText.sentences("1. 論点 [00:00〜06:00]"))
        assertEquals(listOf("一文目。", "二文目？", "三文目"), SpeechText.sentences("一文目。二文目？三文目"))
    }

    @Test fun sentencesSplitLongText() {
        val chunks = SpeechText.sentences("あ".repeat(50), max = 40)
        assertEquals(listOf(40, 10), chunks.map { it.length })
    }

    @Test fun itemsKeepBlockIndex() {
        val items = SpeechText.items(listOf("見出し", "", "A。B。"))
        assertEquals(listOf("見出し" to 0, "A。" to 2, "B。" to 2), items.map { it.text to it.block })
    }
}
