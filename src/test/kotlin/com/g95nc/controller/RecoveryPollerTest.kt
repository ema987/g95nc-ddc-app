package com.g95nc.controller

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryPollerTest {

    private val offStatus = MonitorStatus(Layout.OFF, layoutStatusRaw = 0, paneSources = emptyMap(), audioSource = 0, mainInputSource = 0)

    @Test
    fun testPollUntilRecovered_returnsAsSoonAsStatusIsNonNull() = runTest {
        var calls = 0
        val status = RecoveryPoller.pollUntilRecovered(maxAttempts = 5, pollIntervalMs = 10) {
            calls++
            if (calls < 3) null else offStatus
        }

        assertEquals(3, calls, "should stop retrying the moment a status comes back")
        assertEquals(offStatus, status)
    }

    @Test
    fun testPollUntilRecovered_givesUpAfterMaxAttemptsInsteadOfLoopingForever() = runTest {
        var calls = 0
        val status = RecoveryPoller.pollUntilRecovered(maxAttempts = 4, pollIntervalMs = 10) {
            calls++
            null
        }

        assertEquals(4, calls)
        assertNull(status)
    }

    @Test
    fun testPollUntilRecovered_doesNotDelayAfterTheFinalAttempt() = runTest {
        RecoveryPoller.pollUntilRecovered(maxAttempts = 3, pollIntervalMs = 1000) { null }

        // A delay after attempt 1 and after attempt 2, none after the 3rd (final) attempt.
        assertEquals(2000L, testScheduler.currentTime)
    }

    @Test
    fun testPollUntilRecovered_singleAttemptNeverDelays() = runTest {
        var calls = 0
        val status = RecoveryPoller.pollUntilRecovered(maxAttempts = 1, pollIntervalMs = 5000) {
            calls++
            null
        }

        assertEquals(1, calls)
        assertNull(status)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun testPollUntilRecovered_firstAttemptSucceedsImmediatelyWithNoDelay() = runTest {
        val status = RecoveryPoller.pollUntilRecovered(maxAttempts = 20, pollIntervalMs = 3000) { offStatus }

        assertNotNull(status)
        assertEquals(0L, testScheduler.currentTime)
    }
}
