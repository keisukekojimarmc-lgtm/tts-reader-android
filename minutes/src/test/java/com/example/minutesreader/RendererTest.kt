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

    @Test fun speechRemovesTimestamps() {
        val chunks = SpeechText.chunks("### 1. 論点 [00:00〜06:00]\n\n本文。")
        assertEquals(listOf("1. 論点", "本文。"), chunks)
    }

    @Test fun speechSplitsLongLines() {
        val long = "あ".repeat(30) + "。" + "い".repeat(30) + "。"
        val chunks = SpeechText.chunks(long, max = 40)
        assertEquals(2, chunks.size)
        assertTrue(chunks.all { it.length <= 40 })
    }
}
