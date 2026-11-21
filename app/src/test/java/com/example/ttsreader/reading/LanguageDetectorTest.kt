package com.example.ttsreader.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageDetectorTest {

    @Test
    fun japaneseTextIsJapanese() {
        assertEquals(SpeechLanguage.JAPANESE, LanguageDetector.detect("今日は晴れです。"))
        assertEquals(SpeechLanguage.JAPANESE, LanguageDetector.detect("カタカナ"))
        assertEquals(SpeechLanguage.JAPANESE, LanguageDetector.detect("東京駅"))
    }

    @Test
    fun mixedTextWithAnyJapaneseIsJapanese() {
        assertEquals(SpeechLanguage.JAPANESE, LanguageDetector.detect("iPhone を買った。"))
        assertEquals(SpeechLanguage.JAPANESE, LanguageDetector.detect("Android アプリ"))
    }

    @Test
    fun latinOnlyTextIsEnglish() {
        assertEquals(SpeechLanguage.ENGLISH, LanguageDetector.detect("The quick brown fox."))
        assertEquals(SpeechLanguage.ENGLISH, LanguageDetector.detect("OK"))
    }

    @Test
    fun digitsAndSymbolsAreNeutral() {
        assertNull(LanguageDetector.detectOrNull("2024"))
        assertNull(LanguageDetector.detectOrNull("12:30 - 14:00"))
        assertEquals(SpeechLanguage.JAPANESE, LanguageDetector.detect("2024"))
    }
}
