package com.nutomic.syncthingandroid.webdav.sync

import com.nutomic.syncthingandroid.webdav.persistence.repository.SyncStateRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TransferExecutorTest {
    private lateinit var executor: TransferExecutor
    private lateinit var syncStateRepository: SyncStateRepository
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        syncStateRepository = mockk(relaxed = true)
        executor = TransferExecutor(syncStateRepository)
        tempDir = Files.createTempDirectory("transfer-executor-test").toFile()
    }

    @Test
    fun finalizeDownloadedFile_replacesTargetAtomicallyFromTemp() {
        val target = File(tempDir, "books/a.txt").apply {
            parentFile?.mkdirs()
            writeText("old")
        }
        val temp = File("${target.absolutePath}.part").apply {
            writeText("new-content")
        }

        executor.finalizeDownloadedFile(temp, target, expectedSize = temp.length())

        assertTrue(target.exists())
        assertEquals("new-content", target.readText())
        assertFalse(temp.exists())
    }

    @Test(expected = IllegalStateException::class)
    fun finalizeDownloadedFile_rejectsSizeMismatch() {
        val target = File(tempDir, "books/b.txt")
        val temp = File("${target.absolutePath}.part").apply {
            parentFile?.mkdirs()
            writeText("short")
        }

        executor.finalizeDownloadedFile(temp, target, expectedSize = temp.length() + 10)
    }

    @Test
    fun calculateBackoffMs_growsExponentially() {
        assertEquals(1000L, executor.calculateBackoffMs(1))
        assertEquals(2000L, executor.calculateBackoffMs(2))
        assertEquals(4000L, executor.calculateBackoffMs(3))
    }

    @Test
    fun finalizeDownloadedFile_canPromoteExistingCompletedPartFile() {
        val target = File(tempDir, "books/c.txt")
        val temp = File("${target.absolutePath}.part").apply {
            parentFile?.mkdirs()
            writeText("already-downloaded")
        }

        executor.finalizeDownloadedFile(temp, target, expectedSize = temp.length())

        assertTrue(target.exists())
        assertEquals("already-downloaded", target.readText())
        assertFalse(temp.exists())
    }
}
