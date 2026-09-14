package com.example.ttsreader.reading

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun collapsesWhitespaceAndTrims() {
        assertEquals("a b c", TextNormalizer.normalize("  a \n b\t\tc  "))
    }

    @Test
    fun sameTextWithDifferentSpacingNormalizesEqually() {
        assertEquals(
            TextNormalizer.normalize("見出し\nテキスト"),
            TextNormalizer.normalize("見出し テキスト"),
        )
    }
}
