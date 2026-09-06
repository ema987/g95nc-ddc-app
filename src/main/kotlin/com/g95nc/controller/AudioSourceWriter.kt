package com.g95nc.controller

import kotlinx.coroutines.delay

/**
 * Shared "write E8, verify by settle-confirmed readback, retry the write itself if it didn't
 * stick" logic - previously duplicated near-verbatim across all three [MonitorController]
 * implementations. Each controller talks to the monitor differently (subprocess vs. native
 * DXVA2 calls, different vcp-code string/int conventions), so this only shares the retry
 * algorithm itself via [write]/[read] lambdas, not the transport.
 */
object AudioSourceWriter {
    const val WRITE_ATTEMPTS = 3
    const val SETTLE_CONFIRMATIONS = 2
    const val SETTLE_DELAY_MS = 500L

    /**
     * This monitor doesn't reliably accept every DDC write on the first try (the same class of
     * issue documented in the README for layout writes) - a "successful" write only means the
     * command was sent without error, not that the monitor actually applied it. Read it back and
     * retry the write itself (not just the read) if it didn't stick.
     *
     * The readback itself needs the same "don't trust a single reading" treatment as the status
     * reader's own E8 handling - a lone read right after a write can catch the bus still echoing
     * a stale value in *either* direction, so this polls for [SETTLE_CONFIRMATIONS] consecutive
     * agreeing reads before treating the result as real, not just a single comparison against the
     * target.
     */
    suspend fun setAudioSource(audioCode: Int, write: suspend () -> Boolean, read: suspend () -> Int?): Boolean {
        val target = audioCode and 0xFF
        repeat(WRITE_ATTEMPTS) {
            if (!write()) return false
            if (readSettled(read) == target) return true
        }
        LastControllerError.record(
            RuntimeException("Monitor did not confirm audio source 0x${target.toString(16)} after $WRITE_ATTEMPTS write attempts")
        )
        return false
    }

    private suspend fun readSettled(read: suspend () -> Int?): Int {
        var previous = (read() ?: 0) and 0xFF
        repeat(SETTLE_CONFIRMATIONS) {
            delay(SETTLE_DELAY_MS)
            val current = (read() ?: 0) and 0xFF
            if (current == previous) return current
            previous = current
        }
        return previous
    }
}
