# WebDAV + 墨水屏同步优化项目总表

## 1. 目的

本文档用于统一记录当前仓库中与 `WebDAV` 同步、`墨水屏 / E-Ink` 兼容、`极端场景可靠性`、`产品化 UI` 相关的所有优化项目。

文档分为以下几类：

- 已完成
- 已完成但仍需增强
- 尚未完成
- 产品化后续建议

适用范围：

- `app/src/main/java/com/nutomic/syncthingandroid/webdav/`
- `app/src/main/java/com/nutomic/syncthingandroid/settings/`
- `app/src/main/java/com/nutomic/syncthingandroid/util/EInkUtil.kt`

## 2. 已完成优化项

### 2.1 主链路接入

- 已实现真正存在的 `WebDAVSyncService`
- 已补 `ACTION_SYNC_FOLDER`
- 已补 `ACTION_SYNC_ALL`
- 已补 `ACTION_CANCEL_FOLDER`
- 已补 `ACTION_RETRY_FOLDER`
- 已实现同一 `folderId` 的并发去重
- 已接入前台通知骨架
- 已接入 Dagger 注入

当前价值：

- WebDAV 同步不再只是 `Manifest` 中的占位服务声明
- 可以从应用内部真实触发同步

### 2.2 Room 持久化骨架

- 已实现 `webdav_server_config`
- 已实现 `webdav_folder_config`
- 已实现 `webdav_sync_entry`
- 已实现 `webdav_sync_run`
- 已实现 `webdav_transfer_checkpoint`
- 已接入 DAO
- 已接入 Repository
- 已接入 `SyncthingModule` 提供链

当前价值：

- 双向同步已经拥有稳定的持久化基础
- 同步状态、最近运行结果和恢复信息可以落盘

### 2.3 双向同步规划

- 已实现 `SyncPlanner`
- 已实现 `SyncPlan`
- 已实现 `PlannedSyncAction`
- 已实现本地文件扫描
- 已实现远端目录扫描
- 已实现基于 `snapshot` 的三方比较
- 已支持以下规划动作：
  - `Upload`
  - `Download`
  - `DeleteRemote`
  - `DeleteLocal`
  - `Conflict`

当前价值：

- 系统已经能判断哪些文件应上传、下载、删除或进入冲突
- 不再依赖旧原型里不可靠的“全量偏上传式”判断

### 2.4 基础执行器

- 已实现 `TransferExecutor`
- 已支持上传执行
- 已支持下载执行
- 已支持本地删除
- 已支持远端删除
- 已支持成功后最小 snapshot 回写
- 已支持删除成功后清理 snapshot

当前价值：

- `SyncPlan` 已能真正执行，而不只是停留在规划阶段

### 2.5 安全下载路径

- 已实现下载到 `*.part`
- 已在下载结束后执行 `flush()`
- 已在下载结束后执行 `fd.sync()`
- 已校验下载文件大小
- 已在可行时优先使用原子 `move`
- 已在目标文件系统不支持原子移动时回退

当前价值：

- 下载中断时，不会直接覆盖正式文件
- 应用崩溃或异常退出后，已下载临时文件有机会恢复

### 2.6 错误分类与重试

- 已实现 `WebDAVError`
- 已区分：
  - `Auth`
  - `Timeout`
  - `Network`
  - `NotFound`
  - `LocalState`
  - `Server`
  - `Unknown`
- 已为上传接入 retry/backoff
- 已为下载接入 retry/backoff
- 已为远端删除接入 retry/backoff
- 已实现指数退避：
  - `1s`
  - `2s`
  - `4s`

当前价值：

- 不再对所有错误一视同仁
- 网络瞬态问题与认证错误已经可以走不同路径

### 2.7 墨水屏 profile 与执行节流

- 已实现 `EInkProfile`
- 已实现 `EInkProfileResolver`
- 已支持 profile：
  - `NORMAL`
  - `GENERIC_EINK`
  - `ONYX`
- 已对文石设备设定更保守策略：
  - 更低并发
  - 更低通知刷新频率
  - 更长动作间延迟
  - 更倾向充电时执行
- 已将 profile 接入 `WebDAVSyncService`
- 已将 profile 接入通知节流
- 已将 profile 接入执行器的 action 间隔控制

当前价值：

- 文石等 E-Ink 设备不再沿用普通 LCD 设备的激进执行节奏

### 2.8 墨水屏调度预检查

- 已实现 `SyncExecutionDecision`
- 已实现 `SyncExecutionPolicyEvaluator`
- 已接入以下预检查：
  - `chargingOnly`
  - `wifiOnly / unmetered`
  - `batteryNotLow`
  - `preferCharging`
  - `pauseDuringInteraction`
- 已为被延后的任务引入 `SKIPPED` 状态

当前价值：

- 墨水屏设备在不合适时机不会强行开始同步
- “跳过”与“失败”在状态语义上已经分开

### 2.9 自动恢复调度

- 已实现 `WebDAVSyncScheduler`
- 当前采用 `AlarmManager` 作为最小自动恢复调度器
- 已支持：
  - `SKIPPED` 后自动延后重试
  - 可重试失败后自动延后重试
  - 手动重启或成功后取消旧重试任务

当前价值：

- 任务不再只是“本轮跳过”，而是具备自动恢复机制

### 2.10 传输 checkpoint 骨架

- 已实现 `WebDAVTransferCheckpointEntity`
- 已实现 `WebDAVTransferCheckpointDao`
- 已在 `SyncStateRepository` 暴露 checkpoint 操作
- 已在执行器中为每个动作写入 checkpoint
- 已支持状态：
  - `IN_PROGRESS`
  - `RETRY_PENDING`
  - `FAILED`
  - `INTERRUPTED`
- 已在服务启动时将遗留 `IN_PROGRESS` 转为 `INTERRUPTED`
- 已在同步成功后清理 checkpoint

当前价值：

- 系统已经具备“知道上次哪些动作没做完”的能力

### 2.11 下载侧恢复增强

- 已在 checkpoint 中补充 `transferredBytes`
- 已记录 `.part` 文件路径
- 下载执行前会优先检查对应 `.part`
- 如果 `.part` 文件长度已等于目标长度：
  - 直接 promote 为正式文件
  - 不重新下载

当前价值：

- “下载完成但还没 rename 就中断”的场景已可恢复

### 2.12 产品化第一阶段

- 已在设置页新增 `WebDAV Sync` 入口
- 已实现 server 列表
- 已实现 folder 列表
- 已支持添加、编辑、删除 server
- 已支持添加、编辑、删除 folder
- 已支持“同步全部”
- 已支持“同步单 folder”

当前价值：

- 这套能力已经开始具备用户可配置入口

### 2.13 产品化第二阶段

- 已为 server 表单增加显式校验
- 已为 folder 表单增加显式校验
- 已增加删除确认
- 已增加 `Test connection`
- 已在表单内回显测试结果
- 已在设置页显示最近同步摘要
- 已显示最近运行状态
- 已显示最近传输字节数
- 已显示待恢复项数量
- 已增加 folder 状态详情弹窗
- 已支持在 folder 表单中使用本地路径选择器

当前价值：

- 首次配置成功率更高
- 用户已经能看到更具体的状态，而不是只会“点一下同步”

## 3. 已完成但仍需增强

### 3.1 双向同步正确性

虽然已实现三方比较和最小动作规划，但以下能力仍需继续增强：

- `KEEP_BOTH` 冲突副本的真正执行
- rename / move 识别
- 删除 tombstone 的更完整传播
- 更强的本地/远端版本确认

### 3.2 下载恢复

虽然已支持完整 `.part` 文件恢复，但仍未实现真正的字节级续传：

- 尚未使用 `HTTP Range`
- 尚未使用断点下载
- 不完整 `.part` 目前仍会整文件重下

### 3.3 上传恢复

当前上传仍是全量式：

- 尚未支持断点上传
- 尚未支持分块上传
- 尚未记录更细粒度的上传进度 checkpoint

### 3.4 调度系统

虽然已通过 `AlarmManager` 实现最小自动恢复，但还不是完整后台调度系统：

- 尚未接入 `WorkManager`
- 尚未做开机恢复重建
- 尚未根据系统网络/充电事件即时重新排队

### 3.5 墨水屏产品体验

虽然已有 E-Ink profile 和预检查，但仍需增强：

- 用户手动覆盖设备 profile
- profile UI 设置页
- 阅读活跃期更智能的判断
- 更细粒度的通知样式控制

## 4. 尚未完成的优化项

### 4.1 真正的断点续传

- 下载侧 `Range` 支持
- 上传侧分块/续传
- checkpoint 中记录真实字节进度
- 失败后从 byte offset 恢复而不是全量重下

### 4.2 更完整的冲突处理

- 自动执行 `KEEP_BOTH`
- 冲突副本命名规范化
- 冲突 UI 列表
- 手动冲突处理入口
- 冲突解决后的状态回写

### 4.3 更完整的同步元数据

- `localHash`
- `remoteEtag` 更稳定使用
- `If-Match` / `If-None-Match`
- 更细粒度的远端 metadata 校验

### 4.4 任务恢复与状态机强化

- `RUNNING -> INTERRUPTED -> RETRY_WAIT` 更完整状态流
- run 级重试次数持久化
- 恢复后续跑原因记录
- 结构化 run 日志

### 4.5 UI 状态页深化

- 最近 run 历史列表
- checkpoint 完整列表
- 失败原因详情页
- 更明确的恢复建议
- 冲突项详情

### 4.6 远端路径体验

- 远端路径浏览器
- 远端路径自动补全
- 远端目录创建辅助
- 服务端根路径与子路径可视化

### 4.7 本地路径体验增强

- 更稳定的 `SAF Uri -> path` 解析
- 对无法解析成本地路径的存储提供兼容方案
- 本地目录权限状态提示
- 路径存在性与可写性检查

### 4.8 安全增强

- 密码改用 Android Keystore 包装
- 避免明文回退
- 配置测试时隐藏敏感信息
- 更明确的证书/TLS 错误提示

### 4.9 测试体系增强

- 更完整的 planner 冲突矩阵测试
- checkpoint 恢复测试
- 断网/超时/5xx 集成测试
- 大文件中断恢复测试
- 文石等真机测试计划

## 5. 产品化后续建议

### 5.1 优先级高

- 远端路径浏览器
- 最近 run 历史页
- checkpoint 详情页
- 路径有效性检查
- 密码安全存储升级

### 5.2 优先级中

- 主界面 WebDAV 快捷入口
- folder 状态页中的手动重试/取消
- 失败原因分类标签
- 墨水屏 profile 手动覆盖

### 5.3 优先级低

- 更美观的统计图表
- 更丰富的通知动作
- 批量多 folder 选择同步

## 6. 当前代码落地情况总览

### 6.1 已落地的关键文件

- `app/src/main/java/com/nutomic/syncthingandroid/webdav/WebDAVSyncService.kt`
- `app/src/main/java/com/nutomic/syncthingandroid/webdav/WebDAVSyncScheduler.kt`
- `app/src/main/java/com/nutomic/syncthingandroid/webdav/WebDAVError.kt`
- `app/src/main/java/com/nutomic/syncthingandroid/webdav/sync/SyncPlanner.kt`
- `app/src/main/java/com/nutomic/syncthingandroid/webdav/sync/SyncPlan.kt`
- `app/src/main/java/com/nutomic/syncthingandroid/webdav/sync/TransferExecutor.kt`
- `app/src/main/java/com/nutomic/syncthingandroid/webdav/policy/EInkProfile.kt`
- `app/src/main/java/com/nutomic/syncthingandroid/webdav/policy/EInkProfileResolver.kt`
- `app/src/main/java/com/nutomic/syncthingandroid/webdav/policy/SyncExecutionPolicyEvaluator.kt`
- `app/src/main/java/com/nutomic/syncthingandroid/settings/SettingsWebDAVSyncScreen.kt`

### 6.2 已落地的数据表

- `webdav_server_config`
- `webdav_folder_config`
- `webdav_sync_entry`
- `webdav_sync_run`
- `webdav_transfer_checkpoint`

## 7. 推荐下一步

如果下一阶段继续做底层能力，建议优先：

1. 下载断点续传
2. 上传恢复
3. 更完整冲突处理
4. `WorkManager` 调度替换/补充

如果下一阶段继续做产品化，建议优先：

1. 远端路径浏览器
2. run 历史与 checkpoint 列表
3. 失败原因详情页
4. 路径/权限检查

## 8. 使用方式

建议把本文档当作“当前总表”，并与以下文档配合使用：

- `WEBDAV_EINK_SYNC_REDESIGN.md`：总体设计蓝图
- `WEBDAV_EINK_PHASE1_TASKS.md`：最小可用实现拆解

如果后续继续迭代，建议每完成一轮优化后同步更新本文档中的：

- 已完成优化项
- 已完成但仍需增强
- 尚未完成的优化项
- 推荐下一步
