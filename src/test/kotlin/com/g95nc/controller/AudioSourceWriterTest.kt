package com.g95nc.controller

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioSourceWriterTest {

    @Test
    fun testSetAudioSource_succeedsWhenReadbackMatchesImmediately() = runTest {
        var writes = 0
        val result = AudioSourceWriter.setAudioSource(
            audioCode = 1,
            write = { writes++; true },
            read = { 1 }
        )

        assertTrue(result)
        assertEquals(1, writes)
    }

    @Test
    fun testSetAudioSource_retriesTheWriteUntilReadbackSettlesOnTarget() = runTest {
        var writes = 0
        val result = AudioSourceWriter.setAudioSource(
            audioCode = 1,
            write = { writes++; true },
            read = { if (writes >= 2) 1 else 0 }
        )

        assertTrue(result)
        assertEquals(2, writes)
    }

    @Test
    fun testSetAudioSource_returnsFalseImmediatelyIfTheWriteItselfFails() = runTest {
        var writes = 0
        val result = AudioSourceWriter.setAudioSource(
            audioCode = 1,
            write = { writes++; false },
            read = { 0 }
        )

        assertFalse(result)
        assertEquals(1, writes)
    }

    @Test
    fun testSetAudioSource_givesUpAfterThreeAttemptsAndRecordsError() = runTest {
        LastControllerError.clear()
        val result = AudioSourceWriter.setAudioSource(
            audioCode = 1,
            write = { true },
            read = { 0 }
        )

        assertFalse(result)
        assertTrue(LastControllerError.message?.contains("3 write attempts") == true)
    }
}
