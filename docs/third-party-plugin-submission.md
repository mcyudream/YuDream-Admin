# 第三方插件投稿

第三方作者把插件发到**某套 YuDream 实例的本机市场源**，而不是往 Nexus 交 JAR。订阅方在后台添加该实例的 v2 源后，就能发现并安装。已发布的 `{code}@{pluginVersion}` 不可覆盖，修复必须发新版本。

作者有两条路，选一条即可：

| 路径 | 适合谁 | 发到哪里 | 审核 |
| --- | --- | --- | --- |
| **开放站点投稿** | 把插件交给已开放公开市场的站点（例如高校社团官网） | 该站点的 LOCAL 源，公开 `/market` 可发现 | 由该站点管理员审核；持有跳过审核权限的人可直接上架 |
| **自托管市场源** | 自己跑一套 YuDream，当供给方 | 本机 LOCAL 源，对外提供 v2 协议 | 本机 `reviewRequired` 开关；也可关审核或给自己跳过审核权限 |

两条路用同一套发布通道：后台「插件发布」上传，或 API Key / `publish:market` 流水线。不要再走主仓 MR `submission/` 目录，也不要指望受信发布者往 Nexus 代发——官方仓自己的 Nexus catalog 只服务官方插件，与第三方投稿无关。

操作细节（分类标签、状态机、v2 端点）见[自托管插件市场源](../yd-docs/docs/plugin/market-source.md)。文档站入口：[插件市场与第三方上架](https://ydadocs.yudream.online/plugin/marketplace)。

## 发之前

无论投给开放站点还是自己托管，JAR 都要满足运行时契约：

- 根目录有权威 `plugin.yml`（`name` / `main` / `version`）；`depend` 硬依赖、`softdepend` 软依赖。
- 依赖正式发布的 SPI / SDK / components，JAR 内不得嵌入 `online/yudream/base/plugin/spi/**`。
- 前端产物在 `META-INF/yudream-plugin/frontend/{pluginCode}/remoteEntry.js`。
- `{code}@{pluginVersion}` 在目标源上尚未发布。
- Java `Long` / Snowflake ID 在 JSON 与前端一律用 `string`。

分类、标签、许可证、兼容区间是社区元数据，界面或 `store.json` 提交即可，`plugin.yml` 不携带。完整约定见[插件开发规范](plugin-system/specification.md)。

## 路径一：开放站点投稿

目标站点必须已启用能力 `plugin-market-source`，且公开社区开关 `publicEnabled` 未关。访客在 `/market` 浏览；作者**不能**在公开页上传。

1. 向站点管理员申请账号，以及权限 `platform:plugin-market-source:upload`（后台「插件发布」）。
2. 登录后台 → **平台 → 插件中心 → 插件发布**，上传 JAR。元数据从 JAR 内 `plugin.yml` 解析；分类从内置清单选一项，标签最多 10 个。
3. 默认 `reviewRequired=true` 时进入 `PENDING`，等该站点持有 `accept` 的人通过。站点若关闭审核，或作者另有 `platform:plugin-market-source:publish`，则直接 `PUBLISHED`。
4. 上架后出现在该站 `/market` 与 v2 目录。作者只能改自己的发布物（分类/标签等），编辑不重审；硬删除会去掉记录和 JAR。

流水线投开放站点时，在自己的插件仓配置受保护变量 `YUDREAM_MARKET_URL`（该站 origin，如 `https://www.swustmc.cn`）和 `YUDREAM_MARKET_API_KEY`（勾选 `upload`）。模板 job `publish:market` 只在受保护 `v*` tag 调度。后台「插件发布」页的「查看 CI 模板」可复制完整片段。

公开 `/market` 只发现与下载。其他 YuDream 实例若要安装你的插件，把该站 `{origin}/api/public/plugin-market` 加为 `V2_API` 源即可。

## 路径二：自托管市场源

自己部署 YuDream 后，打开项目闸门 `PLATFORM_PLUGIN_MARKET_SOURCE_ENABLED` 和应用闸门，本机即成为供给方：内置 LOCAL 源 `default`，对外基址 `{origin}/api/public/plugin-market`。

1. 给发布账号 `upload`（以及按需 `accept` / `publish`）。
2. 用「插件发布」或 API Key 把 JAR 发到本机 LOCAL，流程与路径一相同。
3. 需要对外发现时打开 `publicEnabled`；只给受控实例用时关掉公开 `/market`，把 v2 基址私下交给订阅方，并可给源配 Bearer token。
4. 订阅方在「添加市场源」填 `V2_API` + 你的基址（不要带 `/api/v2`）。远程订阅**不依赖**对方是否开启本能力。

能力关闭时不播种 LOCAL、不注册发布/审核/公开 v2 端点，也不回落 Nexus。插件管理、本地上传安装与回滚不受影响。

## 不再使用的投稿方式

主仓 `submission/` + MR + Nexus 代发已经不是第三方上架通道。`templates/plugin-repo/` 里的 `submission.json.example` 仅作历史材料模板；新投稿不要组这套目录，也不要给第三方配置 `NEXUS_USERNAME` / `NEXUS_PASSWORD`。

官方业务插件仍走独立仓受保护 `v*`：可选 Nexus catalog（官方内部）和/或 `publish:market`（自托管市场源）。那是官方发布契约，不是第三方投稿。
