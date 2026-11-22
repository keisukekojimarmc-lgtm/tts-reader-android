package com.example.minutesreader

import com.example.minutesreader.data.MinuteParser
import com.example.minutesreader.dropbox.RemoteFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinuteParserTest {

    private val sample = """
---
date: 2026-11-18
time: "15:00-15:29（録音29分）"
type: 会議
category: CCBJ
title: ABP ファイナンスレビュー（ヘッドカウントの見直し案の確認）
participants: 小島、堀井さん
notion: https://app.notion.com/p/abc （日次ページ）
---

# ABP ファイナンスレビュー（2026-11-18）

> 区分A。3種類を照合しました。

## 概要

HRBP側が、**ヘッドカウント案**を説明した。
ファイナンスからは指摘があった。

## 決定事項

- 比較の基準をJun REに改める。
""".trimStart()

    @Test fun fileNameWithTimeAndCategory() {
        val n = MinuteParser.parseFileName("2026-11-18_1726_研修_ドコモビジネス九州_会議後の確認.md")
        assertEquals("2026-11-18", n.date)
        assertEquals("17:26", n.time)
        assertEquals("研修", n.category)
        assertEquals("ドコモビジネス九州 会議後の確認", n.title)
    }

    @Test fun fileNameWithoutDate() {
        val n = MinuteParser.parseFileName("用語リスト.md")
        assertNull(n.date)
        assertEquals("用語リスト", n.title)
    }

    @Test fun frontMatter() {
        val fm = MinuteParser.splitFrontMatter(sample)
        assertEquals("15:00-15:29（録音29分）", fm.fields["time"])
        assertEquals("CCBJ", fm.fields["category"])
        assertEquals("https://app.notion.com/p/abc （日次ページ）", fm.fields["notion"])
        assertTrue(fm.body.startsWith("# ABP"))
    }

    @Test fun frontMatterListValues() {
        val fm = MinuteParser.splitFrontMatter("---\nparticipants:\n  - 小島\n  - 堀井さん\n---\n本文")
        assertEquals("小島、堀井さん", fm.fields["participants"])
        assertEquals("本文", fm.body)
    }

    @Test fun noFrontMatter() {
        val fm = MinuteParser.splitFrontMatter("# 見出し\n本文")
        assertTrue(fm.fields.isEmpty())
        assertEquals("# 見出し\n本文", fm.body)
    }

    @Test fun summaryFromSection() {
        val s = MinuteParser.extractSummary(MinuteParser.splitFrontMatter(sample).body)
        assertEquals("HRBP側が、ヘッドカウント案を説明した。 ファイナンスからは指摘があった。", s)
    }

    @Test fun buildUsesFrontMatter() {
        val f = RemoteFile("id:1", "2026-11-18_1500_CCBJ_ABP_ファイナンスレビュー.md", "/x.md", "r1", "")
        val m = MinuteParser.build(f, sample)
        assertEquals("ABP ファイナンスレビュー（ヘッドカウントの見直し案の確認）", m.title)
        assertEquals("15:00", m.time)
        assertEquals("11/18(水) 15:00", MinuteParser.shortLabel(m))
        assertEquals("2026年11月", MinuteParser.monthKey(m))
        assertEquals("2026年11月18日(水)", MinuteParser.longDate("2026-11-18"))
    }

    @Test fun ordering() {
        fun f(name: String) = MinuteParser.build(RemoteFile(name, name, name, "r", ""), null)
        val sorted = listOf(
            f("用語リスト.md"),
            f("2026-11-18_1500_A_x.md"),
            f("2026-11-19_0900_A_y.md"),
            f("2026-11-18_1700_A_z.md"),
        ).sortedWith(MinuteParser.ORDER).map { it.name }
        assertEquals(
            listOf("2026-11-19_0900_A_y.md", "2026-11-18_1700_A_z.md", "2026-11-18_1500_A_x.md", "用語リスト.md"),
            sorted,
        )
    }

    @Test fun orderingAscKeepsUndatedLast() {
        fun f(name: String) = MinuteParser.build(RemoteFile(name, name, name, "r", ""), null)
        val sorted = listOf(f("用語リスト.md"), f("2026-11-19_0900_A_y.md"), f("2026-10-01_1000_A_x.md"))
            .sortedWith(MinuteParser.ORDER_ASC).map { it.name }
        assertEquals(listOf("2026-10-01_1000_A_x.md", "2026-11-19_0900_A_y.md", "用語リスト.md"), sorted)
    }
}
