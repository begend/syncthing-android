package com.nutomic.syncthingandroid.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.webdav.WebDAVClient
import com.nutomic.syncthingandroid.webdav.WebDAVFolderRuntimeOptions
import com.nutomic.syncthingandroid.webdav.WebDAVFolderRuntimeOptionsStore
import com.nutomic.syncthingandroid.webdav.WebDAVSyncAction
import com.nutomic.syncthingandroid.webdav.WebDAVSyncService
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVFolderConfigEntity
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVServerConfigEntity
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVSyncRunEntity
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVTransferCheckpointEntity
import com.nutomic.syncthingandroid.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.rememberPreferenceState
import java.util.UUID

fun EntryProviderScope<SettingsRoute>.settingsWebDAVSyncEntry() {
    entry<SettingsRoute.WebDAVSync> {
        SettingsWebDAVSyncScreen()
    }
}

@Composable
fun SettingsWebDAVSyncScreen() {
    val repo = LocalWebDAVConfigRepository.current
    val syncStateRepository = LocalSyncStateRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverConfigs by repo.observeServerConfigs().collectAsState(initial = emptyList())
    val folderConfigs by repo.observeAllFolders().collectAsState(initial = emptyList())
    val autoSyncOnAppOpen = rememberPreferenceState(Constants.PREF_WEBDAV_AUTO_SYNC_ON_APP_OPEN, true)

    var editingServer by remember { mutableStateOf<WebDAVServerConfigEntity?>(null) }
    var editingFolder by remember { mutableStateOf<WebDAVFolderConfigEntity?>(null) }
    var detailsFolder by remember { mutableStateOf<WebDAVFolderConfigEntity?>(null) }
    var isAddingServer by remember { mutableStateOf(false) }
    var isAddingFolder by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.category_webdav_sync),
    ) {
        item {
            SwitchPreference(
                value = autoSyncOnAppOpen.value,
                onValueChange = { autoSyncOnAppOpen.value = it },
                title = { Text(stringResource(R.string.webdav_auto_sync_on_app_open)) },
                summary = { Text(stringResource(R.string.webdav_auto_sync_on_app_open_summary)) },
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.webdav_sync_now_all)) },
                summary = { Text(stringResource(R.string.webdav_sync_now_all_summary)) },
                enabled = folderConfigs.isNotEmpty(),
                onClick = {
                    startWebDAVService(context, WebDAVSyncAction.ACTION_SYNC_ALL)
                    Toast.makeText(context, context.getString(R.string.webdav_sync_started), Toast.LENGTH_SHORT).show()
                },
            )
        }
        item {
            SectionLabel(stringResource(R.string.webdav_servers_title))
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.webdav_add_server)) },
                summary = { Text(stringResource(R.string.webdav_add_server_summary)) },
                onClick = { isAddingServer = true },
            )
        }
        if (serverConfigs.isEmpty()) {
            item {
                Preference(
                    title = { Text(stringResource(R.string.webdav_no_servers)) },
                    enabled = false,
                    onClick = {},
                )
            }
        } else {
            items(serverConfigs.size) { index ->
                val server = serverConfigs[index]
                Preference(
                    title = { Text(server.baseUrl) },
                    summary = { Text("${server.username} • ${server.connectTimeoutMs}/${server.readTimeoutMs} ms") },
                    onClick = { editingServer = server },
                )
            }
        }

        item {
            SectionLabel(stringResource(R.string.webdav_folders_title))
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.webdav_add_folder)) },
                summary = { Text(stringResource(R.string.webdav_add_folder_summary)) },
                enabled = serverConfigs.isNotEmpty(),
                onClick = { isAddingFolder = true },
            )
        }
        if (folderConfigs.isEmpty()) {
            item {
                Preference(
                    title = { Text(stringResource(R.string.webdav_no_folders)) },
                    enabled = false,
                    onClick = {},
                )
            }
        } else {
            items(folderConfigs.size) { index ->
                val folder = folderConfigs[index]
                Preference(
                    title = { Text(folder.localPath) },
                    summary = {
                        Text(buildFolderSummary(folder, syncStateRepository))
                    },
                    onClick = { editingFolder = folder },
                )
                Preference(
                    title = { Text(stringResource(R.string.webdav_sync_now_folder)) },
                    summary = { Text("${folder.localPath} ↔ ${folder.remotePath}") },
                    enabled = folder.enabled,
                    onClick = {
                        startWebDAVService(
                            context = context,
                            action = WebDAVSyncAction.ACTION_SYNC_FOLDER,
                            folderId = folder.id,
                        )
                        Toast.makeText(context, context.getString(R.string.webdav_sync_started), Toast.LENGTH_SHORT).show()
                    },
                )
                Preference(
                    title = { Text(stringResource(R.string.webdav_view_status_details)) },
                    summary = { Text(stringResource(R.string.webdav_view_status_details_summary)) },
                    onClick = { detailsFolder = folder },
                )
                if (index < folderConfigs.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }

    if (isAddingServer || editingServer != null) {
        WebDAVServerDialog(
            initial = editingServer,
            onDismiss = {
                isAddingServer = false
                editingServer = null
            },
            onSave = { server ->
                scope.launch { repo.saveServerConfig(server) }
                isAddingServer = false
                editingServer = null
            },
            onDelete = editingServer?.let {
                {
                    scope.launch { repo.deleteServerConfig(it.id) }
                    editingServer = null
                }
            },
        )
    }

    if (isAddingFolder || editingFolder != null) {
        WebDAVFolderDialog(
            initial = editingFolder,
            initialRuntimeOptions = WebDAVFolderRuntimeOptionsStore.load(
                context,
                editingFolder?.id ?: "new-folder",
            ),
            servers = serverConfigs,
            onDismiss = {
                isAddingFolder = false
                editingFolder = null
            },
            onSave = { folder, runtimeOptions ->
                scope.launch { repo.saveFolderConfig(folder) }
                WebDAVFolderRuntimeOptionsStore.save(context, folder.id, runtimeOptions)
                isAddingFolder = false
                editingFolder = null
            },
            onDelete = editingFolder?.let {
                {
                    scope.launch { repo.deleteFolderConfig(it.id) }
                    WebDAVFolderRuntimeOptionsStore.clear(context, it.id)
                    editingFolder = null
                }
            },
        )
    }

    detailsFolder?.let { folder ->
        WebDAVFolderStatusDialog(
            folder = folder,
            syncStateRepository = syncStateRepository,
            onDismiss = { detailsFolder = null },
        )
    }
}

@Composable
private fun buildFolderSummary(
    folder: WebDAVFolderConfigEntity,
    syncStateRepository: com.nutomic.syncthingandroid.webdav.persistence.repository.SyncStateRepository,
): String {
    val context = LocalContext.current
    val latestRun by syncStateRepository.observeLatestRunForFolder(folder.id).collectAsState(initial = null)
    val checkpointCount by syncStateRepository.observeCheckpointCountForFolder(folder.id).collectAsState(initial = 0)
    val checkpoints by syncStateRepository.observeCheckpointsForFolder(folder.id).collectAsState(initial = emptyList())
    val runtimeOptions = WebDAVFolderRuntimeOptionsStore.load(context, folder.id)

    val statusText = latestRun?.let { latest ->
        val stateLabel = formatRunState(latest)
        val bytesLabel = if (latest.bytesTransferred > 0L) {
            " • ${latest.bytesTransferred} B"
        } else {
            ""
        }
        "$stateLabel$bytesLabel"
    } ?: stringResource(R.string.webdav_status_never_synced)

    val recoveryText = if (checkpointCount > 0) {
        " • " + stringResource(R.string.webdav_status_pending_recovery, checkpointCount)
    } else {
        ""
    }

    val enabledText = if (folder.enabled) {
        stringResource(R.string.webdav_enabled)
    } else {
        stringResource(R.string.webdav_disabled)
    }

    val filterSummary = buildList {
        if (runtimeOptions.allowedExtensions.isNotEmpty()) {
            add(
                stringResource(
                    R.string.webdav_filter_extensions_summary_short,
                    runtimeOptions.extensionsAsInput(),
                )
            )
        }
        runtimeOptions.maxFileSizeBytes?.takeIf { it > 0L }?.let { maxBytes ->
            add(
                stringResource(
                    R.string.webdav_filter_max_size_summary_short,
                    maxBytes / (1024L * 1024L),
                )
            )
        }
    }.joinToString(" • ")

    val filterText = if (filterSummary.isNotBlank()) {
        " • $filterSummary"
    } else {
        ""
    }

    val progressText = buildRealtimeProgressSummary(checkpoints, latestRun)
    val progressSuffix = if (progressText != null) {
        " • $progressText"
    } else {
        ""
    }

    return "${folder.remotePath} • $enabledText$filterText • $statusText$progressSuffix$recoveryText"
}

@Composable
private fun formatRunState(run: WebDAVSyncRunEntity): String {
    return when (run.state) {
        "COMPLETED" -> "Last: " + stringResource(R.string.webdav_status_completed)
        "FAILED" -> "Last: " + stringResource(R.string.webdav_status_failed)
        "SKIPPED" -> "Last: " + stringResource(R.string.webdav_status_skipped)
        "CANCELLED" -> "Last: " + stringResource(R.string.webdav_status_cancelled)
        "RUNNING" -> "Last: " + stringResource(R.string.webdav_status_running)
        else -> "Last: ${run.state.lowercase()}"
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun WebDAVServerDialog(
    initial: WebDAVServerConfigEntity?,
    onDismiss: () -> Unit,
    onSave: (WebDAVServerConfigEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var baseUrl by remember(initial) { mutableStateOf(initial?.baseUrl ?: "") }
    var username by remember(initial) { mutableStateOf(initial?.username ?: "") }
    var password by remember(initial) { mutableStateOf(initial?.passwordAlias ?: "") }
    var connectTimeout by remember(initial) { mutableStateOf((initial?.connectTimeoutMs ?: 15_000).toString()) }
    var readTimeout by remember(initial) { mutableStateOf((initial?.readTimeoutMs ?: 30_000).toString()) }
    var baseUrlError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var timeoutError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showsHttpRiskWarning = baseUrl.trim().startsWith("http://", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) {
                    stringResource(R.string.webdav_server_dialog_add)
                } else {
                    stringResource(R.string.webdav_server_dialog_edit)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        baseUrlError = null
                    },
                    label = { Text(stringResource(R.string.webdav_base_url)) },
                    isError = baseUrlError != null,
                    supportingText = baseUrlError?.let { { Text(it) } },
                )
                if (showsHttpRiskWarning) {
                    Text(
                        text = stringResource(R.string.webdav_http_risk_warning),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        usernameError = null
                    },
                    label = { Text(stringResource(R.string.webdav_username)) },
                    isError = usernameError != null,
                    supportingText = usernameError?.let { { Text(it) } },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = null
                    },
                    label = { Text(stringResource(R.string.webdav_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = passwordError != null,
                    supportingText = passwordError?.let { { Text(it) } },
                )
                OutlinedTextField(
                    value = connectTimeout,
                    onValueChange = {
                        connectTimeout = it
                        timeoutError = null
                    },
                    label = { Text(stringResource(R.string.webdav_connect_timeout)) },
                    isError = timeoutError != null,
                )
                OutlinedTextField(
                    value = readTimeout,
                    onValueChange = {
                        readTimeout = it
                        timeoutError = null
                    },
                    label = { Text(stringResource(R.string.webdav_read_timeout)) },
                    isError = timeoutError != null,
                    supportingText = timeoutError?.let { { Text(it) } },
                )
                if (connectionStatus != null) {
                    Text(connectionStatus!!)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        val validated = validateServerForm(
                            context = context,
                            baseUrl = baseUrl,
                            username = username,
                            password = password,
                            connectTimeout = connectTimeout,
                            readTimeout = readTimeout,
                        )
                        baseUrlError = validated.baseUrlError
                        usernameError = validated.usernameError
                        passwordError = validated.passwordError
                        timeoutError = validated.timeoutError
                        if (!validated.isValid) return@TextButton

                        onSave(
                            WebDAVServerConfigEntity(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                baseUrl = baseUrl.trim(),
                                username = username.trim(),
                                passwordAlias = password,
                                authType = initial?.authType ?: "BASIC",
                                connectTimeoutMs = connectTimeout.toIntOrNull() ?: 15_000,
                                readTimeoutMs = readTimeout.toIntOrNull() ?: 30_000,
                                createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
                TextButton(
                    enabled = !isTestingConnection,
                    onClick = {
                        val validated = validateServerForm(
                            context = context,
                            baseUrl = baseUrl,
                            username = username,
                            password = password,
                            connectTimeout = connectTimeout,
                            readTimeout = readTimeout,
                        )
                        baseUrlError = validated.baseUrlError
                        usernameError = validated.usernameError
                        passwordError = validated.passwordError
                        timeoutError = validated.timeoutError
                        if (!validated.isValid) return@TextButton

                        isTestingConnection = true
                        connectionStatus = context.getString(R.string.webdav_testing_connection)
                        scope.launch {
                            val message = testWebDAVConnection(
                                context = context,
                                config = WebDAVClient.ConnectionConfig(
                                    serverUrl = baseUrl.trim(),
                                    username = username.trim(),
                                    password = password,
                                    authType = WebDAVClient.AuthType.BASIC,
                                    connectTimeoutMs = connectTimeout.toIntOrNull() ?: 15_000,
                                    readTimeoutMs = readTimeout.toIntOrNull() ?: 30_000,
                                )
                            )
                            connectionStatus = message
                            isTestingConnection = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.webdav_test_connection))
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text(stringResource(R.string.delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )

    if (showDeleteConfirm && onDelete != null) {
        ConfirmationDialog(
            title = stringResource(R.string.webdav_confirm_delete_server_title),
            message = stringResource(R.string.webdav_confirm_delete_server_message),
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun WebDAVFolderDialog(
    initial: WebDAVFolderConfigEntity?,
    initialRuntimeOptions: WebDAVFolderRuntimeOptions,
    servers: List<WebDAVServerConfigEntity>,
    onDismiss: () -> Unit,
    onSave: (WebDAVFolderConfigEntity, WebDAVFolderRuntimeOptions) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var localPath by remember(initial) { mutableStateOf(initial?.localPath ?: "") }
    var remotePath by remember(initial) { mutableStateOf(initial?.remotePath ?: "") }
    var selectedServerId by remember(initial, servers) {
        mutableStateOf(initial?.serverId ?: servers.firstOrNull()?.id.orEmpty())
    }
    var enabled by remember(initial) { mutableStateOf(initial?.enabled ?: true) }
    var wifiOnly by remember(initial) { mutableStateOf(initial?.wifiOnly ?: false) }
    var chargingOnly by remember(initial) { mutableStateOf(initial?.chargingOnly ?: false) }
    var batteryNotLow by remember(initial) { mutableStateOf(initial?.batteryNotLow ?: false) }
    var allowedExtensions by remember(initialRuntimeOptions) {
        mutableStateOf(initialRuntimeOptions.extensionsAsInput())
    }
    var maxFileSizeMb by remember(initialRuntimeOptions) {
        mutableStateOf(initialRuntimeOptions.maxFileSizeMbAsInput())
    }
    var localPathError by remember { mutableStateOf<String?>(null) }
    var remotePathError by remember { mutableStateOf<String?>(null) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var filterError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val resolvedPath = FileUtils.getAbsolutePathFromSAFUri(context, uri)
        if (!resolvedPath.isNullOrBlank()) {
            localPath = resolvedPath
            localPathError = null
        } else {
            localPathError = context.getString(R.string.webdav_error_local_path_picker_failed)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) {
                    stringResource(R.string.webdav_folder_dialog_add)
                } else {
                    stringResource(R.string.webdav_folder_dialog_edit)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = localPath,
                    onValueChange = {
                        localPath = it
                        localPathError = null
                    },
                    label = { Text(stringResource(R.string.webdav_local_path)) },
                    isError = localPathError != null,
                    supportingText = localPathError?.let { { Text(it) } },
                )
                TextButton(onClick = { folderPickerLauncher.launch(null) }) {
                    Text(stringResource(R.string.webdav_browse_local_path))
                }
                OutlinedTextField(
                    value = remotePath,
                    onValueChange = {
                        remotePath = it
                        remotePathError = null
                    },
                    label = { Text(stringResource(R.string.webdav_remote_path)) },
                    isError = remotePathError != null,
                    supportingText = remotePathError?.let { { Text(it) } },
                )
                Text(stringResource(R.string.webdav_server_select))
                if (serverError != null) {
                    Text(serverError!!)
                }
                servers.forEach { server ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedServerId = server.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedServerId == server.id,
                            onClick = { selectedServerId = server.id },
                        )
                        Text(server.baseUrl, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                LabeledCheckbox(stringResource(R.string.webdav_enabled), enabled) { enabled = it }
                LabeledCheckbox(stringResource(R.string.webdav_wifi_only), wifiOnly) { wifiOnly = it }
                LabeledCheckbox(stringResource(R.string.webdav_charging_only), chargingOnly) { chargingOnly = it }
                LabeledCheckbox(stringResource(R.string.webdav_battery_not_low), batteryNotLow) { batteryNotLow = it }
                OutlinedTextField(
                    value = allowedExtensions,
                    onValueChange = {
                        allowedExtensions = it
                        filterError = null
                    },
                    label = { Text(stringResource(R.string.webdav_allowed_extensions)) },
                    supportingText = {
                        Text(
                            filterError
                                ?: stringResource(R.string.webdav_allowed_extensions_summary)
                        )
                    },
                    isError = filterError != null,
                )
                OutlinedTextField(
                    value = maxFileSizeMb,
                    onValueChange = {
                        maxFileSizeMb = it
                        filterError = null
                    },
                    label = { Text(stringResource(R.string.webdav_max_file_size_mb)) },
                    supportingText = {
                        Text(
                            filterError
                                ?: stringResource(R.string.webdav_max_file_size_mb_summary)
                        )
                    },
                    isError = filterError != null,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val validated = validateFolderForm(
                        context = context,
                        localPath = localPath,
                        remotePath = remotePath,
                        selectedServerId = selectedServerId,
                    )
                    localPathError = validated.localPathError
                    remotePathError = validated.remotePathError
                    serverError = validated.serverError
                    if (!validated.isValid) return@TextButton
                    val runtimeOptions = runCatching {
                        WebDAVFolderRuntimeOptions.fromInputs(
                            extensionsInput = allowedExtensions,
                            maxFileSizeMbInput = maxFileSizeMb,
                        )
                    }.getOrElse {
                        filterError = context.getString(R.string.webdav_error_filter_invalid)
                        return@TextButton
                    }

                    onSave(
                        WebDAVFolderConfigEntity(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            serverId = selectedServerId,
                            localPath = localPath.trim(),
                            remotePath = remotePath.trim(),
                            syncMode = initial?.syncMode ?: "BIDIRECTIONAL",
                            conflictStrategy = initial?.conflictStrategy ?: "KEEP_BOTH",
                            enabled = enabled,
                            wifiOnly = wifiOnly,
                            chargingOnly = chargingOnly,
                            batteryNotLow = batteryNotLow,
                            maxParallelTransfers = initial?.maxParallelTransfers ?: 1,
                            largeFileThresholdBytes = initial?.largeFileThresholdBytes ?: 256L * 1024L * 1024L,
                            profileId = initial?.profileId,
                            lastSyncAttemptAt = initial?.lastSyncAttemptAt,
                            lastSyncSuccessAt = initial?.lastSyncSuccessAt,
                        ),
                        runtimeOptions,
                    )
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text(stringResource(R.string.delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )

    if (showDeleteConfirm && onDelete != null) {
        ConfirmationDialog(
            title = stringResource(R.string.webdav_confirm_delete_folder_title),
            message = stringResource(R.string.webdav_confirm_delete_folder_message),
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun LabeledCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun startWebDAVService(
    context: Context,
    action: String,
    folderId: String? = null,
) {
    Log.i(
        "SettingsWebDAVSync",
        "Requesting WebDAV sync action=$action folderId=$folderId triggerReason=manual"
    )
    val intent = Intent(context, WebDAVSyncService::class.java).apply {
        this.action = action
        if (folderId != null) {
            putExtra(WebDAVSyncAction.EXTRA_FOLDER_ID, folderId)
        }
        putExtra(WebDAVSyncAction.EXTRA_TRIGGER_REASON, "manual")
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private data class ServerFormValidation(
    val baseUrlError: String?,
    val usernameError: String?,
    val passwordError: String?,
    val timeoutError: String?,
) {
    val isValid: Boolean
        get() = baseUrlError == null && usernameError == null && passwordError == null && timeoutError == null
}

private data class FolderFormValidation(
    val localPathError: String?,
    val remotePathError: String?,
    val serverError: String?,
) {
    val isValid: Boolean
        get() = localPathError == null && remotePathError == null && serverError == null
}

private fun validateServerForm(
    context: Context,
    baseUrl: String,
    username: String,
    password: String,
    connectTimeout: String,
    readTimeout: String,
): ServerFormValidation {
    val baseUrlError = if (baseUrl.trim().isBlank()) {
        context.getString(R.string.webdav_error_base_url_required)
    } else {
        null
    }
    val usernameError = if (username.trim().isBlank()) {
        context.getString(R.string.webdav_error_username_required)
    } else {
        null
    }
    val passwordError = if (password.isBlank()) {
        context.getString(R.string.webdav_error_password_required)
    } else {
        null
    }
    val timeoutError = if ((connectTimeout.toIntOrNull() ?: -1) <= 0 || (readTimeout.toIntOrNull() ?: -1) <= 0) {
        context.getString(R.string.webdav_error_timeout_invalid)
    } else {
        null
    }
    return ServerFormValidation(baseUrlError, usernameError, passwordError, timeoutError)
}

private fun validateFolderForm(
    context: Context,
    localPath: String,
    remotePath: String,
    selectedServerId: String,
): FolderFormValidation {
    val localPathError = if (localPath.trim().isBlank()) {
        context.getString(R.string.webdav_error_local_path_required)
    } else {
        null
    }
    val remotePathError = if (remotePath.trim().isBlank()) {
        context.getString(R.string.webdav_error_remote_path_required)
    } else {
        null
    }
    val serverError = if (selectedServerId.isBlank()) {
        context.getString(R.string.webdav_error_server_required)
    } else {
        null
    }
    return FolderFormValidation(localPathError, remotePathError, serverError)
}

@Composable
private fun WebDAVFolderStatusDialog(
    folder: WebDAVFolderConfigEntity,
    syncStateRepository: com.nutomic.syncthingandroid.webdav.persistence.repository.SyncStateRepository,
    onDismiss: () -> Unit,
) {
    val latestRun by syncStateRepository.observeLatestRunForFolder(folder.id).collectAsState(initial = null)
    val checkpointCount by syncStateRepository.observeCheckpointCountForFolder(folder.id).collectAsState(initial = 0)
    val checkpoints by syncStateRepository.observeCheckpointsForFolder(folder.id).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val progressText = buildRealtimeProgressSummary(checkpoints, latestRun)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.webdav_status_details_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${stringResource(R.string.webdav_local_path)}: ${folder.localPath}")
                Text("${stringResource(R.string.webdav_remote_path)}: ${folder.remotePath}")
                Text("${stringResource(R.string.webdav_status_last_state)}: ${latestRun?.state ?: stringResource(R.string.webdav_status_never_synced)}")
                Text("${stringResource(R.string.webdav_status_last_reason)}: ${latestRun?.errorSummary ?: stringResource(R.string.webdav_status_no_error)}")
                Text("${stringResource(R.string.webdav_status_bytes_transferred)}: ${latestRun?.bytesTransferred ?: 0L} B")
                Text("${stringResource(R.string.webdav_status_pending_recovery_label)}: $checkpointCount")
                progressText?.let {
                    Text("${stringResource(R.string.webdav_status_progress)}: $it")
                }
                if (checkpoints.isNotEmpty()) {
                    checkpoints.take(3).forEach { checkpoint ->
                        Text(
                            "- ${checkpoint.relativePath} • ${checkpoint.state}" +
                                (checkpoint.errorSummary?.let { " • $it" } ?: "")
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    startWebDAVService(
                        context = context,
                        action = WebDAVSyncAction.ACTION_SYNC_FOLDER,
                        folderId = folder.id,
                    )
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.webdav_sync_now_folder))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private suspend fun testWebDAVConnection(
    context: Context,
    config: WebDAVClient.ConnectionConfig,
): String = withContext(Dispatchers.IO) {
    val client = WebDAVClient(context)
    try {
        val connected = client.connect(config)
        if (connected.isFailure) {
            return@withContext context.getString(
                R.string.webdav_test_connection_failed,
                connected.exceptionOrNull()?.message ?: "unknown",
            )
        }

        val tested = client.testConnection()
        return@withContext if (tested.getOrDefault(false)) {
            context.getString(R.string.webdav_test_connection_success)
        } else {
            context.getString(R.string.webdav_test_connection_failed, "unknown")
        }
    } finally {
        client.disconnect()
    }
}

private fun buildRealtimeProgressSummary(
    checkpoints: List<WebDAVTransferCheckpointEntity>,
    latestRun: WebDAVSyncRunEntity?,
): String? {
    val inProgressCheckpoints = checkpoints.filter { it.state == "IN_PROGRESS" }
    if (inProgressCheckpoints.isNotEmpty()) {
        val transferredBytes = inProgressCheckpoints.sumOf { it.transferredBytes ?: 0L }
        val totalBytes = inProgressCheckpoints.sumOf { it.totalBytes ?: 0L }
        val totalItems = checkpoints.size.coerceAtLeast(inProgressCheckpoints.size)
        val itemText = "${inProgressCheckpoints.size}/$totalItems files"
        val byteText = if (totalBytes > 0L) {
            "${formatByteCount(transferredBytes)} / ${formatByteCount(totalBytes)}"
        } else {
            "${formatByteCount(transferredBytes)} transferred"
        }
        return "$itemText • $byteText"
    }

    if (latestRun?.state == "RUNNING") {
        return "Running"
    }

    return null
}

private fun formatByteCount(bytes: Long): String {
    val absBytes = bytes.coerceAtLeast(0L)
    return when {
        absBytes < 1024L -> "${absBytes} B"
        absBytes < 1024L * 1024L -> "${absBytes / 1024L} KB"
        absBytes < 1024L * 1024L * 1024L -> "${absBytes / (1024L * 1024L)} MB"
        else -> "${absBytes / (1024L * 1024L * 1024L)} GB"
    }
}
