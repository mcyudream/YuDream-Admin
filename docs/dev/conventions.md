# 总纲：技术栈、仓库结构、Git 与构建纪律

> **适用范围**：yudream-admim 主仓全部工作。
> **目标**：一份总纲说清技术栈、模块布局、提交纪律、构建验证与通用工程规则；专项规则见同目录专题文件。

本文件是 `docs/dev/` 的入口。修改后端先看 [backend-architecture.md](backend-architecture.md)，插件相关看 [plugin-system.md](plugin-system.md)，权限码看 [permission-codes.md](permission-codes.md)，契约发布看 [contract-release.md](contract-release.md)，宿主版本与发布 tag 看 [release-versioning.md](release-versioning.md)，前端看 [frontend.md](frontend.md)，跨仓同步看 [repo-sync.md](repo-sync.md)。踩坑记录沉淀在 `docs/exp/`。

## 1. 技术栈与环境

| 项 | 要求 |
|---|---|
| JDK | 21（Windows 下跑 Maven **必须**显式设 `JAVA_HOME`，否则误用 JDK 17 报 `invalid target release: 21`） |
| Maven | 3.9+ |
| Node.js | 22.22+ / 24.15+ |
| pnpm | 11.9+（前端**必须**用 pnpm，禁止 npm/yarn） |
| 数据库 | MongoDB；缓存 Redis |
| 前端 | Vue 3.6 + TypeScript + Vite 8 + Pinia + Vue Router 5 + UnoCSS |

Git Bash 环境设置：`export JAVA_HOME='/c/Users/SiberianHusky/.jdks/ms-21.0.10'`。

## 2. 仓库结构与模块布局

| 模块 | 职责 |
|---|---|
| `yudream-domain` | 聚合、值对象、枚举、仓储接口、领域服务；**禁止**框架/Web 依赖 |
| `yudream-application` | cmd/query/dto、应用 assembler、应用 service |
| `yudream-infrastructure` | dataobj、mapper、仓储实现、外部技术网关 |
| `yudream-interfaces` | controller、request/res、接口 assembler、Excel row |
| `yudream-bootstrap` | 启动与装配 |
| `yudream-plugins/yudream-plugin-spi` | 第三方插件唯一允许依赖的编译期契约模块 |
| `yudream-frontend` | 宿主前端与共享包 |

- 官方业务插件的前后端源码一律在独立的 `yudream-admin-plugins` 仓，不再回流本仓。主仓根 Maven reactor 与 GitLab CI 只构建核心模块加 `yudream-plugin-spi`；业务插件按外部仓库对待，运行时从 `plugins/` 目录加载。
- 修改插件架构前先确定受影响边界，先参考行为最接近的既有模块；分层改造以 `system/user` 包为基线。
- **保留用户已有的未提交修改，禁止覆盖或顺手重构无关文件。**

## 3. Git 与提交纪律

1. 每个可独立使用的模块完成后，先运行对应验证，**只暂存该模块文件**并创建中文提交（如 `feat: 完成用户管理模块`、`fix: 修复菜单图标显示`），再进入下一模块。
2. 改动或修正架构规则时，**必须在同一提交同步** `AGENTS.md` 与 `docs/dev/` / `docs/exp/` 对应文件，禁止代码与项目规则脱节。
3. **未经用户明确要求不执行 push/tag 等外部动作**；不纳入 IDE 元数据、临时文件等与任务无关的改动。
4. 宿主版本定级、发布 tag 与 `latest` 规则按 [release-versioning.md](release-versioning.md)：最高级别聚合、纯文档不升版、已 tag 不可移动、失败/变更走新版本而非后缀重打。
5. 涉及双仓同步地图（[repo-sync.md](repo-sync.md)）的修改，同一工作项内同步插件仓；暂不能同步的提交信息标 `[sync-pending]` 并记入同步地图「待同步」节。
6. 若模块因验证失败或工作区存在冲突的用户改动而无法提交，先报告阻塞再继续。

## 4. 构建与验证

后端改动至少执行受影响模块的定向测试或编译：

```bash
mvn -pl <模块> -am -Dtest=<定向测试> -Dsurefire.failIfNoSpecifiedTests=false test
```

前端改动执行目标 app 的 typecheck/build（`test:typecheck` 不覆盖 src 业务源码，前端改动**必须**跑 build 验证）：

```bash
pnpm --dir yudream-frontend --filter @fantastic-admin/core-arco-design-vue run test:typecheck
```

完成后端工作前按 [backend-architecture.md](backend-architecture.md) 的 Review Checklist 执行 Controller 与乱码定向扫描。

注意：`-pl <模块>` 单独解析不到只存在于反应堆的制品（如刚升版的 SPI），**必须**加 `-am`；离线 `-o` 对未发布制品会解析失败。

## 5. Excel / 导入导出

- 使用 EasyExcel；HTTP 文件读写放 `ExcelHttpSupport` 等支撑类，行映射与模板行创建放接口 assembler。
- 前端导出用 blob，并使用 `yudream-frontend/apps/core-arco-design-vue/src/utils/excel.ts` 的既有工具：`saveExcelResponse(response, fallbackName)`、`pickExcelFile(onPick)`、`excelForm(file)`、`importResultMessage(result)`。注意 axios 拦截器把 blob 响应包成 `{data: Blob, headers}`，下载**必须**走 `saveExcelResponse`。
- 新增 `v-auth` 按钮时同步菜单种子枚举权限。
- 后端支撑类：`ExcelHttpSupport.write(response, filename, sheetName, rowClass, rows)` / `importRows(file, rowClass, importer)`（`yudream-interfaces/.../system/excel/support/ExcelHttpSupport.java`）。

## 6. UTF-8 与中文文案

- 领域异常与错误文案使用正常 UTF-8 中文；**禁止** `\uXXXX` 转义和乱码。
- PowerShell 打印乱码**不构成**修改文件的理由，只修复源文件中真实存在的乱码（已解码成鬼字或问号碎片）。
- 前端展示文案保持中文本地化。

## 7. 长 ID 边界

Java `Long`/Snowflake ID 在 JSON、插件 DTO、TS 模型、表单与 URL 参数中一律使用 `string`，**禁止** `Number(id)`——JavaScript 超过 `Number.MAX_SAFE_INTEGER` 丢精度，后续查询/保存会打错记录。领域/存储保持 `Long`，只在 API 边界转字符串，后端应用服务内解析回。全局 Jackson `ToStringSerializer` 连 `long` 计数（total/count/size）也转成字符串，前端做算术前**必须** `Number()`。

## 8. 系统种子

- 系统种子初始化（含菜单枚举种子）必须由配置控制，禁止硬编码覆盖行为。
- 菜单种子同步模式由 `yudream.system.seed.menu.sync-mode` 配置：`INIT_EMPTY`（仅空表初始化）/ `MISSING_ONLY`（只补缺失 code）/ `OVERWRITE`（全量覆盖同 code）。
- 种子策略判断放领域服务；infra 读配置并在启动时把策略传入领域服务。

## 9. 安装向导与引导配置

- 新部署无数据库配置时自动进入安装器切片（`InstallerApplication`，只扫 `interfaces/application/infra/bootstrap` 的 installer 包），排除 Mongo/Redis/邮件自动配置与 `SaTokenDaoForRedisTemplate`（它无条件要求 Redis `StringRedisTemplate`）。
- 引导配置落 `config/yudream-bootstrap.properties`（`YUDREAM_BOOTSTRAP_CONFIG` 可覆盖，Docker 挂载 `./config:/app/config`），`BootstrapPropertiesEnvironmentPostProcessor`（注册于 bootstrap `META-INF/spring.factories`）以第一属性源装载，压过 `MONGO_URI`/`REDIS_*` 环境变量；env 仅作存量部署兜底。
- 运行期业务配置（邮件/S3/站点地址）归管理后台系统配置：DB `sys_setting`（`integration.mail`/`integration.storage`）优先、env 兜底；密钥经 `CapabilityCredentialCipher` 加密（AAD `system.integration.{category}:{key}`），永不回传 UI（只给 `passwordSet`/`secretKeySet` 标志，留空保存不修改）；`MailSenderProvider`/`StorageClientProvider` 按配置哈希重建客户端，管理端编辑**无需重启**即生效。候选配置测试走领域 `IntegrationProbe` 端口、一次性连接。
- `.env` 只保留部署拓扑（镜像/端口/能力闸门）。三层职责不得互相写穿。
- 全套编排 `docker compose -f docker-compose.yml -f docker-compose.platform.yml up -d` 内置 MongoDB/Redis 并向 backend 注入 `YUDREAM_DISCOVERY_*` 提示，安装向导据此自动发现并预填中间件连接。
- 安装器端点：`GET /api/installer/status`、`POST /api/installer/discover`、`POST /api/installer/probe/mongo|redis`、`POST /api/installer/apply`（校验或自动生成 Base64-32 字节 `yudream.credential.key`，原子写文件，发 `InstallerConfigAppliedEvent`，1.5s 后退出由 Docker 重启进正常模式）。

## 10. 文档维护纪律

- `AGENTS.md` 是硬规则唯一入口；`docs/dev/` 是规范层；`docs/exp/` 是坑库。同一事实**只在一处定义**，其他地方引用。
- 坑被根治或上升为约定时，把 `docs/exp/` 条目提炼进 `docs/dev/` 对应规范，并精简或删除坑条目（沉淀环）。
- 写作风格：文件头部用引用块写「适用范围 + 目标」；正文用编号小节；枚举用表格；**必须**/**禁止**加粗；✅/❌ 对照示例；小节标题即结论。
- `docs/plan/`（当前计划层）与 `docs/local.md`（本机密钥）不进版本库；`docs/plans/` 是历史计划归档，冻结不再新增。

## 11. 完成前自检清单

1. 分层归属是否正确？Controller/assembler 是否触碰了禁项？
2. dataobj、Spring Bean、宿主内部类是否泄漏给了插件或接口层？
3. 平台能力是否遵守双闸门与依赖级联？长 ID 是否全程字符串？
4. 新权限码末段动作词是否落入词表（[permission-codes.md](permission-codes.md)），并拆分了查看/写/删除级别？
5. 插件 JAR 的 `plugin.yml`、前端 remote 产物是否完整？软依赖路径是否条件注册？
6. 是否完成目标构建/测试与定向扫描，中文文案无乱码？
7. 是否在同一提交同步了 `AGENTS.md` / `docs/dev/` / `docs/exp/` 与双仓同步地图命中的对方仓文件？

## 12. AOCI 按任务范围读取

本节落实根 `AGENTS.md` 的用户指定读取策略。AOCI 的索引可以完整保留在本地；**模型每次只加载任务需要的部分**。`overview_delivery.chunk_tokens` 只限制单块大小，游标链仍会交付全部正文，不能用调小该值解决上下文溢出。

### 12.1 普通任务使用有限的索引上下文

1. 新会话先读一次 `aoci_rules`；需要核对服务、仓库根、索引身份或漂移时，使用 `aoci_overview(check_only=true)`。规则尚不可靠或需要核对标签时，只读取 `aoci_header`。
2. **每轮 AOCI 认知读取正文累计不超过约 8,000 tokens**，并为业务调查、实施与验证保留上下文。预算包括规则、Header、检查点和索引条目；预算用尽后继续基于已核对的任务源码推进，不通过连续小请求变相读完整个索引。
3. 已知目标时，用 `aoci_get_entries(paths=[...])` 或规范 `object_refs` 精确读取，**每次最多 10 个对象**。未知目标时先用 `rg` 定位任务路径，再读取对应条目。禁止从仓库根读取整个目录或用宽泛搜索枚举全索引。
4. 只在当前任务确实需要新证据时领取下一批读取内容；每批处理完后记录任务结论和必要源码引用。局部认知必须明确标记为局部，不提交虚假的 Whole-Index 交付确认或完整认知 Attestation。

### 12.2 压缩后从任务检查点继续

1. 长任务在被忽略的 `docs/plan/` 中使用独立任务文件保存紧凑检查点：目标、已完成步骤、当前文件路径、索引身份、下一步，以及未完成批次/事务的 receipt、批次身份和 Recovery 状态。不得复制 Whole-Index、Overview 正文、Challenge 或 Attestation 答案。
2. **上下文压缩、语义阈值或阶段切换不触发自动全量重载。** 恢复后先读取检查点，必要时用 `check_only=true` 核对当前身份，再核对当前任务的源码与少量相关条目。身份变化时重新验证受影响证据，不能把旧 receipt 当作当前完整认知证明。
3. 只重新阅读当前步骤确实需要的规则或 Header；不要因缺少完整系统认知而反复从索引第一块开始。

### 12.3 完整读取与索引维护分别执行

1. **只有用户明确要求完整系统认知，且预估正文能装入本轮剩余读取预算时，才启动普通完整 Overview。** 先用 `check_only=true` 核对 `whole_index_tokens`；超预算时报告大小并使用任务范围的局部读取，不启动必然溢出的游标链。
2. 已启动且预算允许的完整交付仍按当前机器协议校验游标、交付身份与 Attestation；不能把分批局部读取拼作已验证的完整认知。
3. 索引保留全部现有受管理内容。创作和维护仍按实时 Guide 读取绑定源码、原子提交当前完整机器批次并处理 Recovery；禁止通过忽略认知资产、改 Baseline 指纹、删条目或调整 Scope 来绕过读取预算。
