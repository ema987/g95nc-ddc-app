package com.g95nc.controller

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The most recent real exception message from a controller's DDC I/O. Every controller already
 * catches its own exceptions to turn a failure into a plain `null`/`false` for the caller - that's
 * the right shape for the calling code, but it also means the actual reason ("no such file or
 * directory", "timed out") previously only ever reached a console most users never see. This lets
 * the one place that already tells the user "couldn't reach the monitor" show that reason too.
 */
object LastControllerError {
    var message: String? by mutableStateOf(null)
        private set

    fun record(e: Exception) {
        e.printStackTrace()
        message = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName
    }

    fun clear() {
        message = null
    }
}
