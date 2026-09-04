package com.g95nc.controller

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingleInstanceLockTest {

    private val tempDir = createTempDirectory("g95nc-lock-test").toFile()

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testAcquire_succeedsWhenNoOneElseHoldsTheLock() {
        val lock = SingleInstanceLock(File(tempDir, "app.lock"))
        assertTrue(lock.acquire())
    }

    @Test
    fun testAcquire_failsWhileAnotherHolderHasTheLock() {
        val path = File(tempDir, "app.lock")
        val firstInstance = SingleInstanceLock(path)
        val secondInstance = SingleInstanceLock(path)

        assertTrue(firstInstance.acquire(), "the first instance should get the lock")
        assertFalse(secondInstance.acquire(), "a second instance must not be able to acquire the same lock")
    }

    @Test
    fun testAcquire_succeedsAgainAfterTheHolderReleases() {
        val path = File(tempDir, "app.lock")
        val firstInstance = SingleInstanceLock(path)
        assertTrue(firstInstance.acquire())
        firstInstance.release()

        val secondInstance = SingleInstanceLock(path)
        assertTrue(secondInstance.acquire(), "releasing should free the lock up for the next launch")
    }

    @Test
    fun testAcquire_createsMissingParentDirectories() {
        val nestedPath = File(tempDir, "nested/does/not/exist/app.lock")
        val lock = SingleInstanceLock(nestedPath)

        assertTrue(lock.acquire())
        assertTrue(nestedPath.exists())
    }
}
