package com.example.ttsreader.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {

    @Test
    fun splitsAtJapaneseSentenceBoundaries() {
        val chunks = TextChunker.chunk("今日は晴れです。明日は雨でしょう！本当ですか？", targetLength = 1)
        assertEquals(listOf("今日は晴れです。", "明日は雨でしょう！", "本当ですか？"), chunks)
    }

    @Test
    fun splitsAtNewlinesAndDropsBlankLines() {
        val chunks = TextChunker.chunk("一行目\n\n二行目\r\n三行目", targetLength = 1)
        assertEquals(listOf("一行目", "二行目", "三行目"), chunks)
    }

    @Test
    fun mergesShortSentencesUpToTargetLength() {
        val chunks = TextChunker.chunk("あ。い。う。え。お。", targetLength = 4)
        assertEquals(listOf("あ。い。", "う。え。", "お。"), chunks)
    }

    @Test
    fun hardSplitsSentencesLongerThanMax() {
        val long = "あ".repeat(25)
        val chunks = TextChunker.chunk(long, maxLength = 10, targetLength = 10)
        assertEquals(listOf("あ".repeat(10), "あ".repeat(10), "あ".repeat(5)), chunks)
        assertTrue(chunks.all { it.length <= 10 })
    }

    @Test
    fun blankInputYieldsNothing() {
        assertEquals(emptyList<String>(), TextChunker.chunk("   \n  "))
    }

    @Test
    fun keepsDecimalPointsInsideSentence() {
        val chunks = TextChunker.chunk("円周率は3.14です。", targetLength = 1)
        assertEquals(listOf("円周率は3.14です。"), chunks)
    }
}
