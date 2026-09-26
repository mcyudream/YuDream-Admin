# 数据备份中心（全量导出 / 合并导入 / 异地备份）

数据备份是 **system 基线能力**（非 platform 动态能力，无双闸门），四层位于 `system/backup` 包，页面在「系统配置 → 数据备份」（`/system/backup`，权限码 `system:backup:*`）。

## 能力总览

- **全量导出**：把系统数据（Mongo 全部业务集合 + 对象存储全部文件）与插件贡献的范围打包为 YDBA 归档（ZIP），本机留存并可在页面下载。
- **合并导入**：上传/下载归档后先分析（各集合缺失/冲突计数、插件范围可用性、主密钥指纹比对），管理员选择「以哪边为准」后一次性合并。语义是**无损并集**：任一端独有的数据始终保留，同标识冲突按策略裁决；永不删除任何一侧数据。
- **异地备份**：目标支持 FTP / FTPS（commons-net）与 WebDAV（JDK HttpClient 实现 PROPFIND/MKCOL/PUT/GET/DELETE）；计划按 Spring 6 位 cron 到点把所选范围归档推送到目标，并按保留份数清理旧档（按 `yudream-backup-` 前缀匹配删除）。
- **异地恢复**：从目标选择远端归档，下载后走同一合并导入流程。

## YDBA 归档协议（schemaVersion 1）

```
manifest.json          # format=yudream-backup、创建时间、宿主版本、主密钥指纹、范围/集合/对象统计
mongo/{collection}.ndjson   # 每行一个 EJSON EXTENDED 文档（Date/Long/Binary/ObjectId 无损往返）
objects/{percent-key}       # 对象存储文件，键逐段百分号编码
objects.index.json          # [{key, size, sha256}]
plugins/{pluginCode}/{scopeCode}/{path}   # 插件备份范围贡献的文件
plugins.index.json          # [{pluginCode, scopeCode, path, size, sha256}]
```

- 集合排除分两层：
  - **硬排除**（不可配置）：Mongo 内部命名空间 `system.*`、视图、备份自身三表（`sysBackupJob`/`sysBackupTarget`/`sysBackupPlan`——恢复会复活旧任务/覆盖新目标，自引用污染）。
  - **默认排除**（可整组覆盖）：高频日志与遥测 `sysApiLog`、`sysLoginLog`、`sysResourceMetric`、`platformAgentExecutionTrace`、`platformRuntimeExecutionLog`、`platformHttpInvocationLog`、`platformGraphQueryLog`；运行时队列与缓存 `platformWikiIngestTask`、`platformPluginMarketSourceSnapshot`；短时效凭据 `oauthAuthorizationCode`、`oauthAccessToken`、`sysRefreshTokenCredential`（备份不携带活体令牌，恢复后用户需重新登录）。
  - 其余全部集合（含 `sysUser/sysRole/sysMenu/sysDept/sysSetting/sysPermission/sysFileObject` 与安全凭据等核心 sys 表）**全部进备份**；**只迁移数据不迁移索引**（Spring Data 注解索引会在写入时自动重建）。
- 合并写入按 500 条一批比对 `_id`（雪花 Long / ObjectId / 字符串按原生类型变体探测）后 `bulkWrite`（LOCAL_WINS 只插缺失；ARCHIVE_WINS upsert 覆盖）。
- 归档清单记录主密钥指纹（`CapabilityCredentialCipher.fingerprint()`），与当前主密钥不一致时在分析与导入时告警：存量加密凭据（邮箱/对象存储/异地目标密码等）可能无法解密，需导入后重新配置。

## 备份范围与插件扩展点

- 系统范围固定 tag `system`；插件范围 tag 为 `plugin:{pluginCode}/{scopeCode}`。
- 插件经 SPI `online.yudream.base.plugin.spi.system.backup` 扩展点注册：

```java
context.registerExtension(PluginBackupProvider.class, new MyBackupProvider(...));
// scopeCode / displayName / description / defaultSchedule（cron 建议）
// ExportReport export(BackupSink sink) —— 只允许 putFile/putText 写相对路径文件；
//   返回的 warnings 会拼进备份任务消息（用于记录局部跳过原因，如某节点离线）
// restore(BackupSource source, PluginBackupConflictStrategy strategy)
```

- 宿主经 `PluginExtensionRegistry.registrations(Class)` 按插件码聚合；插件禁用/卸载后其范围自动从备份中心消失（归档里已有数据在导入时跳过并计入告警）。
- 归档内插件范围由对应 provider 自行解释（文件格式由插件自定义）；宿主统一校验相对路径（禁止穿越/反斜杠/控制字符）。

## 调度与执行

- `BackupJob` 队列由单虚拟线程串行执行（`BackupJobQueueExecutor`），重启后 RUNNING 任务标记失败；进度（phase/percent）持久化，前端 3 秒轮询。
- `BackupPlan` 调度器监听 `BackupPlansChangedEvent` 整体重排；手动触发与到点触发共用 `BackupPlanRunner` 入队。
- 远端连接均为短生命周期（每次操作建连、用毕断开），符合“provider 不持长驻资源”约束。

## 关键配置

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `yudream.system.backup.directory` | `config/backup` | 本机归档与临时文件目录（gitignored、Docker 卷 `./config` 天然持久化） |
| `yudream.system.backup.exclude-collections` | （见上文默认排除清单） | 集合排除规则，逗号分隔，支持「前缀*」通配（如 `plugin_mcpanel__mcpanel_metrics*`）；**设置后整组替换默认清单**，硬排除不受影响；最终生效清单写入归档 `manifest.json` 的 `excludedCollections` |
| `YUDREAM_CREDENTIAL_KEY` | — | 异地目标密码等加密主密钥；跨主机恢复需一致，否则凭据需重配 |

## 权限码

`system:backup:view` / `export` / `download`（查看组）、`import` / `run` / `test`（操作组）、`config`（管理组）、`delete`（危险组），菜单种子在 `SystemMenuModule.BACKUP`。

## 已知边界

- mcpanel 插件注册两个备份范围：`panel-data`（元数据集合 + 节点注册密钥，含敏感凭据需妥善保管归档）与 `server-data`（实例世界数据：逐实例在节点侧 `backup.create` 整包后经分块通道 `backup.download.chunk` 拉取归档；恢复时 `backup.upload.*` 回灌节点、停机后 `backup.restore` 覆盖，并清理回灌副本）。`server-data` 需 mcpanel-node ≥ 0.7.0（caps 广播分块备份通道），旧节点/离线节点自动跳过并把原因拼进任务消息；`LOCAL_WINS` 只恢复数据目录为空的实例，`ARCHIVE_WINS` 停机后无条件覆盖。
- Mongo 索引、GridFS（本系统未使用）不在迁移范围；跨主密钥导入的加密凭据需重新配置。
