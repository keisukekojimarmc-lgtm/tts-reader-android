package com.example.ttsreader.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadHistoryTest {

    @Test
    fun addReturnsTrueOnlyForNewEntries() {
        val history = ReadHistory()
        assertTrue(history.add("a"))
        assertFalse(history.add("a"))
        assertTrue(history.add("b"))
        assertEquals(2, history.size)
    }

    @Test
    fun emptyKeyIsNeverAdded() {
        val history = ReadHistory()
        assertFalse(history.add(""))
        assertEquals(0, history.size)
    }

    @Test
    fun evictsOldestWhenOverCapacity() {
        val history = ReadHistory(capacity = 2)
        history.add("a")
        history.add("b")
        history.add("c")
        assertEquals(2, history.size)
        assertFalse(history.contains("a"))
        assertTrue(history.contains("b"))
        assertTrue(history.contains("c"))
    }

    @Test
    fun clearForgetsEverything() {
        val history = ReadHistory()
        history.add("a")
        history.clear()
        assertTrue(history.add("a"))
    }
}
