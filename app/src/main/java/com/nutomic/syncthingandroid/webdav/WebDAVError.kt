package com.nutomic.syncthingandroid.webdav

import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

sealed class WebDAVError(
    open val messageText: String,
    open val retryable: Boolean,
) {
    data class Auth(override val messageText: String) : WebDAVError(messageText, retryable = false)
    data class Timeout(override val messageText: String) : WebDAVError(messageText, retryable = true)
    data class Network(override val messageText: String) : WebDAVError(messageText, retryable = true)
    data class NotFound(override val messageText: String) : WebDAVError(messageText, retryable = false)
    data class LocalState(override val messageText: String) : WebDAVError(messageText, retryable = false)
    data class Server(override val messageText: String, override val retryable: Boolean) :
        WebDAVError(messageText, retryable)
    data class Unknown(override val messageText: String, override val retryable: Boolean) :
        WebDAVError(messageText, retryable)

    companion object {
        fun classify(error: Throwable): WebDAVError {
            val message = error.message ?: error::class.java.simpleName
            val lowerMessage = message.lowercase()

            return when {
                "401" in lowerMessage || "403" in lowerMessage || "unauthorized" in lowerMessage ||
                    "forbidden" in lowerMessage -> Auth(message)
                "404" in lowerMessage || "not found" in lowerMessage -> NotFound(message)
                "409" in lowerMessage || "412" in lowerMessage || "429" in lowerMessage ||
                    "500" in lowerMessage || "502" in lowerMessage || "503" in lowerMessage ||
                    "504" in lowerMessage -> Server(message, retryable = true)
                error is SocketTimeoutException -> Timeout(message)
                error is UnknownHostException || error is ConnectException || error is SocketException ->
                    Network(message)
                error is FileNotFoundException -> NotFound(message)
                error is IllegalArgumentException || error is IllegalStateException -> LocalState(message)
                error is SSLException && ("certificate" in lowerMessage || "handshake" in lowerMessage) ->
                    Server(message, retryable = false)
                error is IOException -> Network(message)
                else -> Unknown(message, retryable = false)
            }
        }
    }
}
