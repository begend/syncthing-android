package com.nutomic.syncthingandroid.webdav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class WebDAVErrorTest {
    @Test
    fun classify_marksTimeoutAsRetryable() {
        val error = WebDAVError.classify(SocketTimeoutException("timed out"))

        assertTrue(error.retryable)
        assertTrue(error is WebDAVError.Timeout)
    }

    @Test
    fun classify_marksUnknownHostAsRetryableNetwork() {
        val error = WebDAVError.classify(UnknownHostException("no host"))

        assertTrue(error.retryable)
        assertTrue(error is WebDAVError.Network)
    }

    @Test
    fun classify_marksUnauthorizedAsNonRetryable() {
        val error = WebDAVError.classify(IllegalStateException("401 unauthorized"))

        assertFalse(error.retryable)
        assertTrue(error is WebDAVError.Auth)
    }
}
