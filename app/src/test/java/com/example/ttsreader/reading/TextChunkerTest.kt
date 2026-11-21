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
        assertEquals(listOf("Pi is 3.14 exactly."), TextChunker.chunk("Pi is 3.14 exactly.", targetLength = 1))
    }

    @Test
    fun splitsEnglishSentencesAtPeriodFollowedBySpace() {
        val chunks = TextChunker.chunk("Hello world. See example.com now. Bye.", targetLength = 1)
        assertEquals(listOf("Hello world.", "See example.com now.", "Bye."), chunks)
    }
}

class TextChunkerLanguageTest {

    @Test
    fun splitsWhereLanguageChanges() {
        val chunks = TextChunker.chunkWithLanguage("これは日本語です。This is English. 続きは日本語。")
        assertEquals(
            listOf(
                SpeechChunk("これは日本語です。", SpeechLanguage.JAPANESE),
                SpeechChunk("This is English.", SpeechLanguage.ENGLISH),
                SpeechChunk("続きは日本語。", SpeechLanguage.JAPANESE),
            ),
            chunks,
        )
    }

    @Test
    fun mergesConsecutiveSentencesOfSameLanguage() {
        val chunks = TextChunker.chunkWithLanguage("First sentence. Second sentence.")
        assertEquals(listOf(SpeechChunk("First sentence. Second sentence.", SpeechLanguage.ENGLISH)), chunks)
    }

    @Test
    fun neutralSentenceInheritsSurroundingLanguage() {
        val chunks = TextChunker.chunkWithLanguage("Chapter one.\n2024\nIt was cold.")
        assertEquals(listOf(SpeechChunk("Chapter one. 2024 It was cold.", SpeechLanguage.ENGLISH)), chunks)
    }

    @Test
    fun neutralOnlyTextDefaultsToJapanese() {
        val chunks = TextChunker.chunkWithLanguage("2024")
        assertEquals(listOf(SpeechChunk("2024", SpeechLanguage.JAPANESE)), chunks)
    }
}
