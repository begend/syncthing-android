package com.nutomic.syncthingandroid.webdav

object WebDAVSyncAction {
    const val ACTION_SYNC_FOLDER = "com.nutomic.syncthingandroid.webdav.action.SYNC_FOLDER"
    const val ACTION_SYNC_ALL = "com.nutomic.syncthingandroid.webdav.action.SYNC_ALL"
    const val ACTION_CANCEL_FOLDER = "com.nutomic.syncthingandroid.webdav.action.CANCEL_FOLDER"
    const val ACTION_RETRY_FOLDER = "com.nutomic.syncthingandroid.webdav.action.RETRY_FOLDER"

    const val EXTRA_FOLDER_ID = "com.nutomic.syncthingandroid.webdav.extra.FOLDER_ID"
    const val EXTRA_TRIGGER_REASON = "com.nutomic.syncthingandroid.webdav.extra.TRIGGER_REASON"
    const val EXTRA_RUN_ID = "com.nutomic.syncthingandroid.webdav.extra.RUN_ID"
}
