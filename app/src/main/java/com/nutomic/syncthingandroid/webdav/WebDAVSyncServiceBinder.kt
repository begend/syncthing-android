package com.nutomic.syncthingandroid.webdav

import android.os.Binder

class WebDAVSyncServiceBinder(
    private val service: WebDAVSyncService,
) : Binder() {
    fun getService(): WebDAVSyncService = service
}
