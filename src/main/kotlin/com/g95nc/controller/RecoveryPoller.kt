package com.g95nc.controller

import kotlinx.coroutines.delay

/**
 * Retries a status read with a bounded number of attempts instead of forever, so a monitor that
 * genuinely never comes back (unplugged, powered off) doesn't leave the UI polling indefinitely.
 */
object RecoveryPoller {
    const val DEFAULT_MAX_ATTEMPTS = 20
    const val DEFAULT_POLL_INTERVAL_MS = 3000L

    /** @return the first non-null status, or null once [maxAttempts] have all failed. */
    suspend fun pollUntilRecovered(
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
        getStatus: suspend () -> MonitorStatus?
    ): MonitorStatus? {
        for (attempt in 1..maxAttempts) {
            val status = getStatus()
            if (status != null) return status
            if (attempt != maxAttempts) delay(pollIntervalMs)
        }
        return null
    }
}
