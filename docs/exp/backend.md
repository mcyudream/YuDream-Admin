# 后端经验：分层、Mongo、能力与数据处理

> **适用范围**：主仓 Java 后端与运维数据路径。
> **目标**：保留能缩短故障定位的后端症状、根因与验证方法。

## 1. 值对象和权限上下文

### 部门/角色切换时“未加入该部门”或权限全失

- **现象**：用户角色明明属于部门，切换部门仍提示未加入；或切换后无权限。
- **根因**：`DeptID`/`RoleID`/`UserID`/`PermissionID` 值对象曾缺少 `equals/hashCode`，集合与归属判断按对象引用比较。角色分配同时受部门约束，先切部门再加载角色才是正确路径。
- **规避**：值对象按值比较；角色列表只展示当前部门角色；切换后解析该部门角色，历史会话按部门优先并回落持有角色。
- **来源**：`id-value-object-equality`、`role-bound-to-dept`；规则见 `docs/dev/backend-architecture.md`。

### 权限重建后授权似乎消失

- **现象**：插件权限短暂未注册或权限目录重建后，已有用户/角色授权被拒绝或丢失。
- **根因**：把“注册目录当前不存在”误当成授权无效，或重建流程触发了播种/插件启停。
- **规避**：存储层原样保留未知权限码；展示层仅隐藏不存在项；重建前可回读备份并校验角色/用户/菜单指纹，只改 `sysPermission`，不播种、不重启插件。
- **来源**：`role-permission-codes-passthrough`、`permission-management-views`；`docs/system/permission-management.md`。

## 2. Mongo 与备份

### 插件热重载第二次保存触发乐观锁异常

- **现象**：同一个插件模块先保存 unloaded 再保存 enabled 时，出现 `OptimisticLockingFailureException`。
- **根因**：MongoTemplate.save 会增加 DO 的 `@Version`，但不回写原领域实例；第二次保存仍带旧版本。
- **规避**：仓储保存后把 `saved.getVersion()` 回写领域对象，参照 `WikiSourceRepoImpl`。
- **来源**：`plugin-mongo-version-writeback`。

### 合并备份导入撞唯一键或启动权限同步崩溃

- **现象**：恢复报 E11000；或重复 `sysPermission/sysMenu/sysRole` 记录让启动 `toMap` 冲突。
- **根因**：负数雪花 ID 未生成 Long 变体；只按 `_id` 判重，忽略 code/username/key 等业务唯一键。
- **规避**：规范化 ID 同时生成多类型变体（负号不可被数字判断漏掉）；`BackupBusinessKeys` 按业务键判重，LOCAL_WINS 跳过、ARCHIVE_WINS 先 purge 再写；权限同步 keep-first 自愈。全局 JSON Long 是字符串，前端数量运算前显式转 Number。
- **来源**：`merge-import-id-variants`；`docs/system/backup.md`。

### S3 上传因 HeadBucket 被拒绝

- **现象**：RustFS/S3 对象上传失败，但对象操作权限看似正常。
- **根因**：服务端拒绝 `HeadBucket`，不代表对象读写不可用。
- **规避**：不能只因 bucket head 检查受限就拒绝上传；按实际对象操作能力处理。
- **来源**：`s3-rustfs-bucket-head`。

### 大文件上传中途超时或返回 500

- **现象**：地图 ZIP 等大文件上传被 60 秒客户端超时、Tomcat 截断或落成 500。
- **根因**：axios 默认超时；nginx/Tomcat 多层大小限制不一致；超限异常进入通用 500。
- **规避**：multipart 与 nginx `client_max_body_size` 对齐（当前 10240MB），Tomcat `max-http-form-post-size/max-swallow-size=-1`；超限异常映射 400「上传文件超过大小限制」；宿主与插件大文件 XHR 用 `timeout: 0`。
- **来源**：`host-multipart-archive-limit`。

## 3. Installer 与环境属性

### 新安装因为 Redis Bean 缺失启动失败

- **现象**：无数据库配置时 installer slice 仍因 `StringRedisTemplate` 或 Sa-Token DAO Bean 缺失失败。
- **根因**：`SaTokenDaoForRedisTemplate` 不受 Redis 自动配置排除影响，仍无条件要求 Redis Bean。
- **规避**：installer slice 同时排除 Mongo/Redis/Mail 自动配置和 `cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate`。
- **来源**：`installer-slice-autoconfig`。

### EnvironmentPostProcessor 因未解析占位符抛异常

- **现象**：新安装无 `MONGO_URI` 时读取 `spring.data.mongodb.uri` 抛 `PlaceholderResolutionException`；运行期读取 `${MAIL_USERNAME}` 也可能触发。
- **根因**：`Environment.getProperty()` 会解析 yml 占位符；无默认值的占位符无法解析。
- **规避**：EPP 从原始 PropertySource 读取；运行时对含未解析占位符的属性同样防护，不要认为加了其他安全默认就能解决嵌套 placeholder。
- **来源**：`installer-bootstrap-placeholder`、`env-getproperty-placeholder`。

### 管理后台保存邮件/S3 配置后必须重启才生效

- **现象**：数据库配置已改但仍使用旧客户端，或密钥意外回传 UI。
- **根因**：运行客户端未按有效配置重建，或把机密值作为表单回显。
- **规避**：DB Setting 优先、env 兜底；密钥加密存储、UI 仅返回 configured 标志；provider 按配置哈希重建并关闭旧客户端；空值保存保留旧密钥。
- **来源**：`integration-config-precedence`。

### Neo4j 历史密文在启用时变成 500

- **现象**：升级后已有 Neo4j 密码无法解密，enable 返回 500。
- **根因**：旧密文 AAD 是 `neo4j:password`，新格式使用 `capability:neo4j:password`；mapper 解密失败保留 `v1:` 后旧 provider 又抛 IllegalStateException。
- **规避**：解密时兼容旧 AAD；enable 对仍带密文前缀的值再次解密；失败转业务异常 400，不抛 IllegalStateException。
- **来源**：`neo4j-credential-aad-and-bizexception`。

## 4. 图形、渲染与导入

### 空关系图误判为关系数据

- **现象**：应为折线/柱状图却只显示空坐标轴。
- **根因**：`nodes: []`、`links: []` 的空数组在 JavaScript 为真，用 `nodes && links` 推断 chart 类型会误判。
- **规避**：只按明确 `chartType` 分支，不能由数组是否存在推断图类型。
- **来源**：`dataviz-empty-graph-arrays`。

### 表单上传配置保存后公开页上传失效

- **现象**：form-create designer 中上传规则编辑器可用，持久化 JSON 到公开/runtime 渲染后上传回调丢失。
- **根因**：JSON 无法保存 `onSuccess/customRequest` 函数 props。
- **规避**：运行时重新注入上传 handler、规范文件 URL 预览，并提供公开表单专属上传端点，不依赖登录态 `/api/files/upload`。
- **来源**：`form-create-upload-runtime`。

### GrapesJS 新增 HTML 后 CSS 丢失

- **现象**：CMS AI 插入 HTML，预览类样式缺失或校验误报。
- **根因**：GrapesJS 默认裁剪未命中组件的 CSS；先写 CSS 再插入 HTML 时 class 尚不存在；getCss 未保留未使用样式。
- **规避**：先插入 HTML 再应用 CSS/JS；`getCss` 保留 `keepUnusedStyles`；后端对本次 HTML 的全部 class 执行 CSS 覆盖校验。
- **来源**：`cms-add-html-style-order`。

### 模板渲染服务容器内健康但宿主不可达

- **现象**：后端在宿主运行、render 服务在 Docker，访问 `127.0.0.1:3000/health` 失败。
- **根因**：compose 只有 `expose`，没有发布宿主端口。
- **规避**：compose 显式 `ports: "${RENDER_PORT}:3000"`，重建后验证宿主 health。
- **来源**：`render-server-host-port`。

### 渲染菜单后样式被清理

- **现象**：HTML 菜单预览丢失 style tag 或内联样式。
- **根因**：渲染服务对 HTML/CSS 做 sanitize-html 或 CSS allowlist。
- **规避**：原样保留 style tag、inline style 与调用方 CSS；隔离依靠关闭 JS、CSP 与阻止文档子资源，不靠删除样式。
- **来源**：`render-server-style-preservation`。

### 备份记录数与导出项不一致

- **现象**：某些核心配置未备份，或敏感日志/令牌混入归档。
- **根因**：只用统一白名单/黑名单而未区分硬排除与可配置排除。
- **规避**：硬排除系统内部表/视图/备份自身；日志遥测令牌类走可覆盖配置，生效清单写入 manifest；核心 `sys*` 业务表纳入；跨主机导入先核 fingerprint。
- **来源**：`system-backup-capability`；`docs/system/backup.md`。
