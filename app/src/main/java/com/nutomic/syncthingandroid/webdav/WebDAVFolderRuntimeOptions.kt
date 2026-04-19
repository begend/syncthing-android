package com.nutomic.syncthingandroid.webdav

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.Locale

data class WebDAVFolderRuntimeOptions(
    val allowedExtensions: Set<String> = emptySet(),
    val maxFileSizeBytes: Long? = null,
) {
    fun shouldInclude(relativePath: String, sizeBytes: Long?): Boolean {
        if (allowedExtensions.isNotEmpty()) {
            val extension = relativePath.substringAfterLast('.', "").lowercase(Locale.US)
            if (extension.isBlank() || extension !in allowedExtensions) {
                return false
            }
        }
        val maxSize = maxFileSizeBytes
        if (maxSize != null && maxSize > 0L && sizeBytes != null && sizeBytes > maxSize) {
            return false
        }
        return true
    }

    fun extensionsAsInput(): String {
        return allowedExtensions.sorted().joinToString(",") { ".$it" }
    }

    fun maxFileSizeMbAsInput(): String {
        val maxSize = maxFileSizeBytes ?: return ""
        if (maxSize <= 0L) return ""
        return (maxSize / BYTES_PER_MB).toString()
    }

    companion object {
        private const val BYTES_PER_MB = 1024L * 1024L

        fun fromInputs(
            extensionsInput: String,
            maxFileSizeMbInput: String,
        ): WebDAVFolderRuntimeOptions {
            val normalizedExtensions = extensionsInput
                .split(',', '\n', ';', ' ')
                .mapNotNull { token ->
                    val normalized = token.trim().removePrefix(".").lowercase(Locale.US)
                    normalized.takeIf { it.isNotBlank() }
                }
                .toSet()

            val maxFileSizeBytes = maxFileSizeMbInput.trim()
                .takeIf { it.isNotBlank() }
                ?.toLong()
                ?.takeIf { it > 0L }
                ?.times(BYTES_PER_MB)

            return WebDAVFolderRuntimeOptions(
                allowedExtensions = normalizedExtensions,
                maxFileSizeBytes = maxFileSizeBytes,
            )
        }
    }
}

object WebDAVFolderRuntimeOptionsStore {
    private const val KEY_ALLOWED_EXTENSIONS_PREFIX = "webdav_folder_allowed_extensions_"
    private const val KEY_MAX_FILE_SIZE_BYTES_PREFIX = "webdav_folder_max_file_size_bytes_"

    fun load(context: Context, folderId: String): WebDAVFolderRuntimeOptions {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val extensions = preferences.getString(KEY_ALLOWED_EXTENSIONS_PREFIX + folderId, "").orEmpty()
        val maxFileSizeBytes = preferences.getLong(KEY_MAX_FILE_SIZE_BYTES_PREFIX + folderId, 0L)
            .takeIf { it > 0L }
        return WebDAVFolderRuntimeOptions.fromInputs(
            extensionsInput = extensions,
            maxFileSizeMbInput = maxFileSizeBytes?.div(1024L * 1024L)?.toString().orEmpty(),
        )
    }

    fun save(context: Context, folderId: String, options: WebDAVFolderRuntimeOptions) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(KEY_ALLOWED_EXTENSIONS_PREFIX + folderId, options.extensionsAsInput())
            .putLong(KEY_MAX_FILE_SIZE_BYTES_PREFIX + folderId, options.maxFileSizeBytes ?: 0L)
            .apply()
    }

    fun clear(context: Context, folderId: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(KEY_ALLOWED_EXTENSIONS_PREFIX + folderId)
            .remove(KEY_MAX_FILE_SIZE_BYTES_PREFIX + folderId)
            .apply()
    }
}
