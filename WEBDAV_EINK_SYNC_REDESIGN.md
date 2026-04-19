# WebDAV + 墨水屏自动同步重构设计

## 1. 目标

本文档面向以下产品目标：

1. 支持文石（Onyx Boox）等墨水屏设备的自动同步。
2. 支持本地目录与 WebDAV 服务之间的可靠双向同步。
3. 能处理大文件、网络不稳定、网络中断、重复触发、后台恢复等极端场景。
4. 与现有 Syncthing Android 的服务、前台通知、运行条件体系兼容。

本文档不假设当前 `app/src/main/java/com/nutomic/syncthingandroid/webdav/` 下的实现已经可直接上线；它更像是下一阶段的落地蓝图。

## 2. 现状结论

当前仓库里的 `webdav/` 与 `E-Ink` 代码更接近“实验性原型”，主要问题如下：

- 主流程未真正接入：`AndroidManifest.xml` 声明了 `WebDAVSyncService`，但源码中不存在对应服务实现。
- 双向同步未完成：当前 `SyncEngine` 缺少稳定的“上次同步快照”模型，无法正确判断修改、删除、冲突。
- 冲突处理未接线：`ConflictResolver` 存在，但未融入主同步决策链。
- 弱网与大文件恢复不足：没有断点续传、临时文件、原子替换、任务 checkpoint、稳定重试策略。
- 墨水屏优化偏 UI：当前大多是通知/UI 频率优化，没有真正影响调度和数据平面。

结论：要满足产品目标，需要从“脚本式同步逻辑”升级为“可恢复的同步系统”。

## 3. 目标架构

建议采用以下分层：

```text
UI / Settings
├── WebDAV 设置页
├── 墨水屏 profile 设置
└── 文件夹同步配置页

Scheduling Layer
├── WorkManager 周期/条件调度
├── Boot / App update 恢复入口
└── 手动触发入口

Execution Layer
├── WebDAVSyncService (foreground)
├── WebDAVSyncOrchestrator
└── SyncSession

Sync Core
├── SyncPlanner
├── SyncStateStore
├── ConflictResolver
├── TransferExecutor
└── RetryPolicy / NetworkPolicy

Transport Layer
└── WebDAVClient

Persistence Layer
├── Room / SQLite
├── Sync snapshots
├── Tombstones
└── Retry queue / checkpoints

Device Adaptation
├── EInkProfileResolver
├── EInkPolicy
└── Notification/UI throttling
```

## 4. 核心职责拆分

### 4.1 `WebDAVSyncService`

职责：

- 作为所有 WebDAV 同步任务的唯一执行服务。
- 在长任务时切到前台，显示同步通知。
- 串联运行条件、网络检查、电量检查、Wi-Fi 检查。
- 保证同一 folder 不会并发执行多个同步会话。

建议接口：

```kotlin
class WebDAVSyncService : LifecycleService() {
    suspend fun runFolderSync(folderId: String, reason: SyncTriggerReason)
    suspend fun runAllEligibleFolders(reason: SyncTriggerReason)
    fun cancelFolderSync(folderId: String)
}
```

### 4.2 `WebDAVSyncOrchestrator`

职责：

- 加载 folder 配置与 server 配置。
- 构建本次 `SyncContext`。
- 选择设备 profile、并发度、网络策略、重试策略。
- 调用 `SyncPlanner` 生成计划，再由 `TransferExecutor` 执行。

### 4.3 `SyncPlanner`

职责：

- 基于“三方状态”生成同步计划。
- 检测新增、修改、删除、冲突、移动。
- 决定是上传、下载、删除、保留冲突副本还是进入人工处理。

### 4.4 `SyncStateStore`

职责：

- 持久化上次成功同步的快照。
- 记录 tombstone（删除标记）。
- 记录进行中的传输 checkpoint。
- 支持崩溃/重启后恢复。

### 4.5 `TransferExecutor`

职责：

- 执行上传、下载、删除、重命名、冲突副本创建。
- 管理并发度、重试、取消、限速、临时文件、原子落盘。
- 将进度上报给通知层与 UI 层。

### 4.6 `EInkProfileResolver`

职责：

- 解析设备是否为墨水屏。
- 返回品牌/型号对应的 profile。
- 只影响调度、刷新策略、通知频率、后台行为，不影响同步正确性。

## 5. 双向同步模型

### 5.1 三方比较

每个同步对象都要比较三份状态：

1. `LocalCurrent`
2. `RemoteCurrent`
3. `LastSyncedSnapshot`

只有基于三方比较，才能区分以下情况：

- 本地新增
- 远端新增
- 本地修改
- 远端修改
- 本地删除
- 远端删除
- 双端同时修改
- 一端删除、一端修改

### 5.2 建议持久化字段

```text
SyncEntry
- folderId
- relativePath
- localExists
- remoteExists
- localSize
- remoteSize
- localMtime
- remoteMtime
- localHash
- remoteEtag
- remoteContentLength
- remoteLastModified
- lastSyncTime
- status
- tombstoneSide
```

说明：

- `localHash` 可用于增强大文件冲突判断，避免单纯依赖 mtime。
- `remoteEtag` 是 WebDAV 最值得利用的版本标识。
- `tombstone` 用于可靠传播删除，防止“刚删掉就被另一端恢复”。

### 5.3 同步决策矩阵

简化规则如下：

```text
Last=none, Local=present, Remote=none    -> upload
Last=none, Local=none, Remote=present    -> download
Last=present, Local=changed, Remote=same -> upload
Last=present, Local=same, Remote=changed -> download
Last=present, Local=deleted, Remote=same -> remote delete
Last=present, Local=same, Remote=deleted -> local delete
Last=present, Local=changed, Remote=changed -> conflict
Last=present, Local=deleted, Remote=changed -> conflict
Last=present, Local=changed, Remote=deleted -> conflict
```

### 5.4 冲突策略

建议支持以下策略：

- `KEEP_BOTH`
- `NEWEST_WINS`
- `LOCAL_WINS`
- `REMOTE_WINS`
- `MANUAL_RESOLVE`

默认建议使用 `KEEP_BOTH`，理由：

- 对用户最安全。
- 避免误覆盖阅读资料、笔记、扫描件等高价值文件。
- 更适合第一版上线。

## 6. 数据库设计建议

建议用 Room 新增以下表：

### 6.1 `webdav_server_config`

```text
- id
- baseUrl
- username
- encryptedPasswordRef
- authType
- connectTimeoutMs
- readTimeoutMs
- trustMode
- createdAt
- updatedAt
```

说明：

- 密码不要继续和密钥一起放在 `SharedPreferences`。
- 优先使用 Android Keystore 包装敏感凭据。

### 6.2 `webdav_folder_config`

```text
- id
- localPath
- remotePath
- syncMode
- conflictStrategy
- enabled
- wifiOnly
- chargingOnly
- batteryNotLow
- maxParallelTransfers
- largeFileThresholdBytes
- fileFiltersJson
- excludePatternsJson
- profileId
- lastSyncAttemptAt
- lastSyncSuccessAt
```

### 6.3 `webdav_sync_entry`

```text
- folderId
- relativePath
- localSize
- localMtime
- localHash
- remoteEtag
- remoteSize
- remoteMtime
- lastSyncTime
- lastSeenLocal
- lastSeenRemote
- isDeletedLocal
- isDeletedRemote
- version
```

### 6.4 `webdav_transfer_checkpoint`

```text
- folderId
- relativePath
- direction
- tempFilePath
- transferredBytes
- totalBytes
- remoteEtag
- updatedAt
- retryCount
- state
```

### 6.5 `webdav_sync_run`

```text
- runId
- folderId
- triggerReason
- state
- startedAt
- endedAt
- successCount
- failureCount
- conflictCount
- bytesTransferred
- errorSummary
```

## 7. 传输与极端场景处理

### 7.1 下载策略

要求：

- 一律先下载到 `*.part` 临时文件。
- 完成后校验大小/ETag/mtime。
- 校验通过后再原子替换正式文件。
- 如果中途失败，保留 checkpoint，下一次尝试恢复。

推荐流程：

```text
prepare temp file
-> resume or restart download
-> flush + fsync
-> verify metadata
-> rename temp to final
-> update snapshot
```

### 7.2 上传策略

分两档：

- `V1`：不做真正分块上传，但要有失败重试与重入保护。
- `V2`：如果服务端支持，增加分块/断点续传。

V1 至少要做到：

- 上传前读取本地文件快照。
- 上传中若本地文件发生变化，取消本次上传并重新规划。
- 上传成功后再次确认远端 metadata。

### 7.3 大文件策略

建议默认阈值：`256MB`。

超过阈值时：

- 强制单文件串行，不与其它大文件并发。
- 增加超时。
- 关闭高频 UI 更新。
- 优先在 Wi-Fi + 充电条件下执行。
- 允许用户设置“仅手动同步大文件”。

### 7.4 弱网与网络波动

建议分层处理：

- 连接失败：指数退避重试。
- DNS / TLS / 认证失败：快速失败，不无限重试。
- 读超时：可重试。
- 网络切换（Wi-Fi -> 蜂窝 / 断网 -> 恢复）：暂停并重新建立会话。
- 远端列举失败：整轮同步失败，绝不能降级成空目录。

### 7.5 取消与恢复

必须支持：

- 用户取消当前 folder sync。
- 应用被杀后恢复。
- 服务重启后恢复未完成任务。
- 重复触发去重。

建议：

- 同一 `folderId` 使用单飞锁。
- `SyncRun` 进入 `RUNNING` 状态后，未正常结束则下次恢复为 `INTERRUPTED`。

## 8. WebDAVClient 重构建议

当前 `WebDAVClient` 需要从“薄封装”升级为“传输层”。

### 8.1 必须补齐的能力

- 真正应用连接超时和读超时。
- 分类错误类型：
  - auth error
  - network unreachable
  - timeout
  - server 5xx
  - conflict / precondition failed
- 暴露 `HEAD/PROPFIND` 能力以获取 metadata。
- 支持条件请求：
  - `If-Match`
  - `If-None-Match`
- 如果底层库不支持 resume，要明确在上层用 checkpoint + restart 方式兜底。

### 8.2 建议接口

```kotlin
interface WebDAVTransport {
    suspend fun stat(path: String): Result<RemoteStat?>
    suspend fun list(path: String): Result<List<RemoteEntry>>
    suspend fun upload(
        localFile: File,
        remotePath: String,
        options: UploadOptions
    ): Result<RemoteStat>
    suspend fun download(
        remotePath: String,
        tempFile: File,
        options: DownloadOptions
    ): Result<FileDownloadResult>
    suspend fun delete(path: String, precondition: RemotePrecondition?): Result<Unit>
    suspend fun move(from: String, to: String, precondition: RemotePrecondition?): Result<Unit>
}
```

## 9. 墨水屏兼容方案

### 9.1 设计原则

墨水屏兼容不能建立在“误判一个普通低 DPI 设备也没关系”的假设上。

建议：

- 用 `profile` 模式替代纯启发式。
- “检测”与“策略”解耦。
- 允许用户在设置里手动覆盖自动检测结果。

### 9.2 设备 profile

建议定义：

```text
EInkProfile
- id
- brand
- modelPatterns
- supportsPartialRefresh
- supportsA2
- prefersForegroundSync
- notificationUpdateIntervalMs
- uiUpdateIntervalMs
- pauseDuringInteraction
- maxParallelTransfers
- preferredSyncWindows
- chargingPreferred
```

### 9.3 文石（Onyx Boox）优先策略

建议优先落地的文石 profile：

- 默认降低并发到 `1-2`。
- 前台同步优先，避免后台被系统 aggressively 杀死。
- 阅读活跃时延后同步。
- 通知更新频率降到 `5-10s`。
- 只在进度显著变化、完成、失败、冲突时刷新 UI。
- 大文件仅在 Wi-Fi 且最好在充电时执行。

### 9.4 用户交互策略

对 E-Ink 设备：

- 减少实时进度刷新。
- 用阶段性状态替代高频百分比。
- 冲突处理界面尽量简化：
  - 保留本地
  - 保留远端
  - 保留两份
- 对长任务提供“后台继续 / 稍后同步”。

## 10. 调度方案

建议统一使用两层调度：

### 10.1 `WorkManager`

负责：

- 周期触发
- 开机恢复
- 网络恢复后重试
- 充电/Wi-Fi 等系统约束

### 10.2 `ForegroundService`

负责：

- 执行当前同步 session
- 展示同步通知
- 提供 cancel / retry action
- 在长任务期间保持进程优先级

### 10.3 与现有运行条件整合

应尽量复用已有 Run Conditions 思路：

- Wi-Fi only
- Charging only
- Battery not low
- Metered network disallowed
- Roaming disallowed
- Scheduled window

但不要直接把 Syncthing daemon 状态和 WebDAV session 强耦合；两者应该是平级能力。

## 11. 状态机建议

```text
IDLE
-> QUEUED
-> PRECHECK
-> SCANNING_LOCAL
-> SCANNING_REMOTE
-> PLANNING
-> EXECUTING
   -> UPLOADING
   -> DOWNLOADING
   -> DELETING
   -> CONFLICT_HANDLING
-> COMMITTING_SNAPSHOT
-> COMPLETED

Error branches:
PRECHECK / SCANNING / EXECUTING / COMMITTING
-> RETRY_WAIT
-> FAILED
-> INTERRUPTED
```

要求：

- snapshot 只在“本轮成功提交”后更新。
- 中途失败不能污染上次成功快照。
- 每个阶段要有结构化日志和 runId。

## 12. 测试计划

### 12.1 单元测试

至少覆盖：

- 三方比较矩阵
- 删除传播
- 同时修改冲突
- `KEEP_BOTH` 路径
- checkpoint 恢复
- ETag 变化判定
- profile 解析

### 12.2 集成测试

建议准备可控 WebDAV 测试服务：

- 正常上传/下载
- 认证失败
- 断网
- 服务器超时
- `5xx`
- 大文件中途失败
- 目录列举异常

### 12.3 真机测试

优先设备：

- Onyx Boox Note Air
- Onyx Boox Palma
- Bigme
- Hisense A9

重点验证：

- 后台存活
- 阅读时是否被打断
- 大文件耗时和失败恢复
- 冲突提示是否可理解
- 电量消耗

## 13. 分阶段实施建议

### Phase 1: 打通最小可用链路

- 实现 `WebDAVSyncService`
- 引入 Room
- 落地 `folder config` 和 `sync snapshot`
- 完成单 folder 单次同步主链
- 支持 upload/download/delete 的基础双向比较

验收标准：

- 能在普通 Android 设备上稳定跑通单 folder 双向同步
- 中断后不会产生损坏文件

### Phase 2: 增强可靠性

- 增加 checkpoint 与 retry policy
- 原子下载替换
- 大文件策略
- 网络恢复与服务重启恢复
- 完整冲突策略

验收标准：

- 弱网/中断/应用重启后可恢复
- 双端同时修改时不会静默覆盖

### Phase 3: 墨水屏专项

- 引入 `EInkProfileResolver`
- 支持 Onyx profile
- 同步/通知/UI 节流
- 用户手动覆盖设备 profile

验收标准：

- 文石等设备上体验稳定
- 不因墨水屏优化影响同步正确性

### Phase 4: 体验与扩展

- 批量 folder 调度
- 更细粒度的统计和日志
- 高级过滤规则
- 手动冲突解决界面

## 14. 实施优先级

如果只做一轮高价值开发，建议优先顺序如下：

1. `WebDAVSyncService + Room snapshot`
2. `SyncPlanner` 三方比较
3. 原子下载 / checkpoint / retry
4. 冲突策略默认 `KEEP_BOTH`
5. Onyx Boox profile

## 15. 不建议继续沿用的做法

以下做法建议废弃或降级：

- 用 `lastSyncTime = 0` 推断本地修改。
- 远端列举失败后当成空目录继续同步。
- 在没有 snapshot 的情况下自称“双向同步”。
- 仅依赖 `mtime` 判定冲突。
- 墨水屏识别使用 `"android"` 这类宽泛厂商名。
- 下载时直接覆盖正式文件。
- 将密码和加密密钥保存在同一个 `SharedPreferences`。

## 16. 推荐下一步

建议下一步直接做以下产出：

1. 先实现数据库 schema 与 DAO。
2. 再实现 `WebDAVSyncService` 和 `SyncPlanner`。
3. 用一个最小可控 folder 跑通：
   - 本地新增 -> 上传
   - 远端新增 -> 下载
   - 本地删除 -> 远端删除
   - 双端修改 -> 冲突副本
4. 最后接入 E-Ink profile 与通知节流。

如果进入编码阶段，建议先按 Phase 1 拆成 3-5 个可提交的小 PR，而不是一次性重写全部 `webdav/` 目录。
