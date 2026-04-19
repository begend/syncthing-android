package com.nutomic.syncthingandroid.webdav.policy

data class SyncExecutionDecision(
    val shouldRun: Boolean,
    val reason: String,
)
