package com.g95nc.controller

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * Guards against a second instance running concurrently. Two instances both polling the same
 * ddcutil/betterdisplaycli channel is exactly the kind of contention that produces the timeouts
 * and flakiness this app exists to avoid - so only one instance should ever talk to the monitor
 * at a time.
 *
 * Backed by an OS-level advisory file lock (not a PID file): the OS releases it automatically
 * when the process exits, cleanly or not, so there's no stale-lock cleanup to get wrong.
 */
class SingleInstanceLock(private val lockFilePath: File) {
    private var raf: RandomAccessFile? = null
    private var fileLock: FileLock? = null

    /** True if this process now holds the lock (safe to proceed); false if another instance
     * already holds it. On an unrelated I/O error (e.g. an unwritable directory), fails open -
     * an environment problem here shouldn't be the reason the app refuses to launch at all. */
    fun acquire(): Boolean {
        return try {
            lockFilePath.parentFile?.mkdirs()
            val opened = RandomAccessFile(lockFilePath, "rw")
            val lock = try {
                opened.channel.tryLock()
            } catch (e: OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                opened.close()
                false
            } else {
                raf = opened
                fileLock = lock
                true
            }
        } catch (e: Exception) {
            true
        }
    }

    fun release() {
        try {
            fileLock?.release()
        } catch (e: Exception) {
            // best effort
        }
        try {
            raf?.close()
        } catch (e: Exception) {
            // best effort
        }
        fileLock = null
        raf = null
    }
}
