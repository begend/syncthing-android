# WebDAV + 墨水屏自动同步 Phase 1 任务拆解

## 1. 范围

Phase 1 的目标不是一次性做完所有高级能力，而是先打通“最小可用链路”：

- 有真正的 `WebDAVSyncService`
- 有最小可用的 Room 持久化
- 能对单个 folder 做可靠的基础双向同步
- 支持新增、修改、删除
- 下载不再直接覆盖正式文件
- 出现错误时能安全失败，不静默破坏数据

本阶段暂不强求：

- 真正的断点续传
- 分块上传
- 完整的手动冲突 UI
- 全量 E-Ink profile 体系

## 2. 交付标准

完成 Phase 1 后，至少满足：

1. 用户可以配置一个 WebDAV server 和一个 folder。
2. 可以手动触发单 folder 同步。
3. 本地新增文件会上传到远端。
4. 远端新增文件会下载到本地。
5. 本地删除会传播到远端。
6. 远端删除会传播到本地。
7. 双端同时修改不会静默覆盖，默认进入 `KEEP_BOTH`。
8. 下载中断不会留下损坏的正式文件。
9. 同一 folder 不会并发跑多次同步。

## 3. 建议拆成 5 个 PR

### PR-1: 数据层骨架

目标：

- 引入 Room
- 建立最小表结构
- 提供 DAO 和 repository

### PR-2: WebDAVSyncService 与调度入口

目标：

- 提供手动触发入口
- 跑通前台服务
- 单 folder 单会话执行

### PR-3: 同步扫描与规划

目标：

- 扫描本地与远端
- 基于 snapshot 生成计划
- 支持 create/modify/delete/conflict

### PR-4: 传输执行与安全落盘

目标：

- 上传、下载、删除执行器
- `*.part` 临时文件
- 原子替换
- 失败安全退出

### PR-5: 基础 E-Ink 策略

目标：

- 只做最小 E-Ink 策略接入
- 文石优先
- 仅影响并发、通知频率、同步窗口

## 4. 建议新建的包与类

建议新增如下结构：

```text
app/src/main/java/com/nutomic/syncthingandroid/webdav/
├── service/
│   ├── WebDAVSyncService.kt
│   ├── WebDAVSyncServiceBinder.kt
│   └── WebDAVSyncAction.kt
├── scheduler/
│   ├── WebDAVSyncWorker.kt
│   ├── WebDAVSyncScheduler.kt
│   └── WebDAVSyncConstraints.kt
├── sync/
│   ├── WebDAVSyncOrchestrator.kt
│   ├── SyncPlanner.kt
│   ├── SyncPlan.kt
│   ├── SyncSession.kt
│   ├── SyncPreflightChecker.kt
│   ├── TransferExecutor.kt
│   ├── TransferProgress.kt
│   └── FolderSyncMutex.kt
├── persistence/
│   ├── WebDAVDatabase.kt
│   ├── entity/
│   │   ├── WebDAVServerConfigEntity.kt
│   │   ├── WebDAVFolderConfigEntity.kt
│   │   ├── WebDAVSyncEntryEntity.kt
│   │   └── WebDAVSyncRunEntity.kt
│   ├── dao/
│   │   ├── WebDAVServerConfigDao.kt
│   │   ├── WebDAVFolderConfigDao.kt
│   │   ├── WebDAVSyncEntryDao.kt
│   │   └── WebDAVSyncRunDao.kt
│   └── mapper/
│       └── WebDAVEntityMapper.kt
├── policy/
│   ├── EInkProfileResolver.kt
│   ├── EInkProfile.kt
│   └── SyncExecutionPolicy.kt
└── model/
    ├── SyncDiff.kt
    ├── SyncTriggerReason.kt
    ├── SyncConflictDecision.kt
    └── RemoteStat.kt
```

说明：

- 不建议继续把所有逻辑都塞进现有 `SyncEngine.kt`。
- 可以保留现有 `WebDAVClient.kt`，但 Phase 1 中建议让它逐步退化成 transport 层。

## 5. 数据库任务拆解

## 5.1 建表范围

Phase 1 只需要 4 张表：

### `webdav_server_config`

字段建议：

```text
id: String
baseUrl: String
username: String
passwordAlias: String
authType: String
connectTimeoutMs: Int
readTimeoutMs: Int
createdAt: Long
updatedAt: Long
```

### `webdav_folder_config`

字段建议：

```text
id: String
serverId: String
localPath: String
remotePath: String
syncMode: String
conflictStrategy: String
enabled: Boolean
wifiOnly: Boolean
chargingOnly: Boolean
batteryNotLow: Boolean
maxParallelTransfers: Int
largeFileThresholdBytes: Long
profileId: String?
lastSyncAttemptAt: Long?
lastSyncSuccessAt: Long?
```

### `webdav_sync_entry`

字段建议：

```text
folderId: String
relativePath: String
localSize: Long?
localMtime: Long?
localFingerprint: String?
remoteEtag: String?
remoteSize: Long?
remoteMtime: Long?
lastSyncTime: Long
existsLocal: Boolean
existsRemote: Boolean
deletedLocal: Boolean
deletedRemote: Boolean
updatedAt: Long
```

### `webdav_sync_run`

字段建议：

```text
runId: String
folderId: String
triggerReason: String
state: String
startedAt: Long
endedAt: Long?
successCount: Int
failureCount: Int
conflictCount: Int
bytesTransferred: Long
errorSummary: String?
```

## 5.2 DAO 任务

每个 DAO 至少提供：

- `insertOrReplace(...)`
- `getById(...)`
- `deleteById(...)`
- `observeEnabledFolders()`
- `getEntriesForFolder(folderId)`
- `upsertEntries(folderId, entries)`
- `deleteMissingEntries(folderId, keepPaths)`

## 5.3 Repository 任务

建议补一个 `WebDAVConfigRepository` 和一个 `SyncStateRepository`。

职责：

- 对上层屏蔽 Room entity
- 负责枚举值映射
- 负责密码 alias 解析
- 负责 run 记录更新

## 5.4 验证点

- Room schema 能编译
- DAO 基础 CRUD 测试通过
- folder config 与 sync entry 能正常序列化/反序列化

## 6. WebDAVSyncService 任务拆解

## 6.1 服务职责

服务只负责：

- 接收 action
- 串联 orchestrator
- 切前台通知
- 同 folder 去重
- 管理取消

服务不直接负责：

- 扫描 diff
- 冲突决策
- 文件传输细节

## 6.2 建议 action

```text
ACTION_SYNC_FOLDER
ACTION_SYNC_ALL
ACTION_CANCEL_FOLDER
ACTION_RETRY_FOLDER
```

## 6.3 建议 extra

```text
EXTRA_FOLDER_ID
EXTRA_TRIGGER_REASON
EXTRA_RUN_ID
```

## 6.4 关键实现点

- 使用 `LifecycleService`
- 使用 `SupervisorJob + CoroutineScope`
- 用 `Mutex` 或 `ConcurrentHashMap<String, Job>` 做 folder 级单飞
- 同步运行中展示 foreground notification
- 完成后自动 `stopForeground()` / `stopSelfResult()`

## 6.5 验证点

- 手动发送 `ACTION_SYNC_FOLDER` 能启动服务
- 同一个 `folderId` 连续触发两次时只执行一次
- 取消 action 能取消当前 job

## 7. 同步规划任务拆解

## 7.1 先做最小同步对象

Phase 1 只处理文件，不处理目录 move 优化。

目录策略：

- 本地目录不存在时创建
- 远端目录不存在时创建
- rename/move 暂时降级为 `delete + create`

## 7.2 需要实现的扫描结果模型

```kotlin
data class LocalSnapshot(...)
data class RemoteSnapshot(...)
data class SyncDiff(...)
```

建议 `SyncDiff` 至少包含：

- `uploads`
- `downloads`
- `remoteDeletes`
- `localDeletes`
- `conflicts`

## 7.3 规划逻辑顺序

1. 扫描本地文件树
2. 拉取远端目录树
3. 加载 `webdav_sync_entry`
4. 对每个 `relativePath` 做三方比较
5. 生成 `SyncPlan`
6. 如果存在 conflict，默认生成 `KEEP_BOTH`

## 7.4 默认冲突策略

Phase 1 建议：

- 默认 `KEEP_BOTH`
- 文件命名格式：
  - `name (local conflict yyyyMMdd-HHmmss).ext`
  - `name (remote conflict yyyyMMdd-HHmmss).ext`

## 7.5 删除传播规则

必须明确：

- `snapshot 有，本地无，远端未变` -> 远端删除
- `snapshot 有，远端无，本地未变` -> 本地删除
- `snapshot 有，本地无，远端变了` -> conflict
- `snapshot 有，远端无，本地变了` -> conflict

## 7.6 验证点

- 单元测试覆盖 create / modify / delete / conflict 基本矩阵
- 远端列举失败时，本轮同步直接失败
- 没有 snapshot 时，不对删除做激进传播

## 8. 传输执行任务拆解

## 8.1 上传

Phase 1 的上传最小要求：

- 执行前再次确认本地文件仍存在
- 上传成功后重新 `stat(remotePath)`
- 记录新的 snapshot

## 8.2 下载

Phase 1 的下载必须改成：

1. 下载到 `targetPath + ".part"`
2. 下载完成后 `flush`
3. 关闭流
4. 校验大小
5. 重命名覆盖正式文件

如果失败：

- 删除残留 `.part` 或标记为中断
- 不覆盖正式文件

## 8.3 删除

Phase 1 删除传播需要：

- 本地删除：删除文件前先判断本地文件是否已被用户重建
- 远端删除：删除前再次确认远端对象仍存在

## 8.4 并发度

Phase 1 建议：

- 默认并发 `2`
- 大文件并发 `1`
- E-Ink 设备默认并发 `1`

## 8.5 进度

Phase 1 不要求精确到字节级 UI 展示，但至少要有：

- 当前阶段
- 当前文件
- 已完成文件数 / 总文件数
- 当前 runId

## 8.6 验证点

- 下载失败后正式文件保持原样
- 上传成功后 snapshot 被更新
- 大文件时不会同时并发多个大文件

## 9. WebDAVClient Phase 1 改造点

## 9.1 必做项

- 让 `connectTimeoutMs` / `readTimeoutMs` 真正生效
- `listDirectory()` 失败时返回错误，不吞异常
- 暴露 `stat(path)` 能力
- 下载接口支持写入指定临时文件
- 所有错误归类为统一错误模型

## 9.2 建议新增错误模型

```kotlin
sealed class WebDAVError {
    data class Auth(val message: String) : WebDAVError()
    data class Network(val message: String) : WebDAVError()
    data class Timeout(val message: String) : WebDAVError()
    data class Server(val code: Int?, val message: String) : WebDAVError()
    data class NotFound(val path: String) : WebDAVError()
    data class Unknown(val cause: Throwable) : WebDAVError()
}
```

## 9.3 验证点

- 认证失败能区分出来
- 超时能区分出来
- `listDirectory` 异常不再被误当空目录

## 10. E-Ink Phase 1 最小任务

Phase 1 不做复杂 profile 体系，只做最小策略落地。

## 10.1 新建 `EInkProfileResolver`

返回：

```kotlin
data class EInkProfile(
    val id: String,
    val isEInk: Boolean,
    val isOnyx: Boolean,
    val maxParallelTransfers: Int,
    val notificationUpdateIntervalMs: Long,
    val uiUpdateIntervalMs: Long,
    val preferCharging: Boolean
)
```

## 10.2 默认策略

普通设备：

- `maxParallelTransfers = 2`
- `notificationUpdateIntervalMs = 1000`

文石设备：

- `maxParallelTransfers = 1`
- `notificationUpdateIntervalMs = 5000`
- `uiUpdateIntervalMs = 3000`
- `preferCharging = true`

## 10.3 现有代码改造建议

- `EInkUtil` 先移除明显宽泛的厂商命中，例如 `"android"`。
- `EInkSyncManager` 不再试图修改数据模型本身。
- `EInkNotificationManager` 只负责展示策略，不参与同步决策。

## 10.4 验证点

- 文石设备默认走低并发
- 通知更新频率变低
- 普通 Android 设备不受影响

## 11. 建议的开发顺序

建议按下面顺序推进：

1. 建 Room entity / DAO / repository
2. 补 `WebDAVSyncService`
3. 补 `SyncPlanner`
4. 补 `TransferExecutor`
5. 改 `WebDAVClient`
6. 接 `EInkProfileResolver`

理由：

- 先有状态持久化，双向同步才有依据。
- 先有服务入口，后面才能做真实联调。
- 先有 planner，再有 executor，职责更清晰。

## 12. 建议的验收测试清单

## 12.1 最小手工用例

准备：

- 本地空目录 `Books`
- WebDAV 远端空目录 `Books`

验证：

1. 本地放入 `a.txt`，手动同步，远端出现 `a.txt`
2. 远端放入 `b.txt`，手动同步，本地出现 `b.txt`
3. 本地删除 `a.txt`，手动同步，远端删除 `a.txt`
4. 远端删除 `b.txt`，手动同步，本地删除 `b.txt`
5. 双端同时修改 `c.txt`，手动同步，生成冲突副本

## 12.2 失败保护用例

1. 下载中途断网，不覆盖正式文件
2. 远端列举失败，本轮直接失败
3. 同 folder 连点两次同步，不会起两个任务
4. 取消同步后能正常停掉前台任务

## 12.3 E-Ink 用例

1. 文石设备上通知更新频率明显降低
2. 同步期间阅读操作不出现高频刷新
3. 大文件在文石设备上默认串行

## 13. 风险与规避

### 风险 1

现有 `webdav/` 代码与新架构并存时，容易重复逻辑。

规避：

- 将旧 `SyncEngine` 标记为 legacy / prototype
- 新逻辑全部进入 `sync/` 子包

### 风险 2

Room 和旧 `SharedPreferences` 配置并存期间会出现双写问题。

规避：

- Phase 1 先做读旧写新
- 等新链路稳定后再移除旧配置入口

### 风险 3

弱网问题在模拟器不容易复现。

规避：

- 尽早准备可控 WebDAV 测试环境
- 增加集成测试和真机测试

## 14. 完成标志

当以下条件全部满足，可认为 Phase 1 完成：

- `WebDAVSyncService` 存在并能真正运行
- Room 持久化可用
- 三方比较可用
- 新增/删除/修改/冲突最小矩阵通过
- 下载改为临时文件 + 原子替换
- 文石默认低并发策略可生效

## 15. 完成后下一步

Phase 1 完成后，立刻进入 Phase 2：

- checkpoint
- retry/backoff
- run 恢复
- 原子上传增强
- 更完整的冲突处理

如果你准备开始编码，建议就按 `PR-1 -> PR-5` 的顺序来推进，不建议跳过数据层直接改 `SyncEngine.kt`。
