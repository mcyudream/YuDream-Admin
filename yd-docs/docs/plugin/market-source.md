# 自托管插件市场源

「插件市场源」能力（code `plugin-market-source`，能力中心「插件分发」分组）让每个 YuDream 实例同时作为**订阅方**与**供给方**：

- 订阅方：在后台「插件市场」合并安装多个源中的插件（见[插件市场与第三方上架](./marketplace)第 6 节）；
- 供给方：本机发布物构成默认市场源，对外提供 v2 协议；其他实例把本机 v2 源基址添加为 `V2_API` 源即可订阅。

能力关闭时**不注册本机源、不回落 Nexus**。后台市场列表为空，公开 `/market` 不可用；插件管理、本地上传与回滚不受影响。

## 1. 启用能力

- 项目闸门：`yudream.platform.capabilities.plugin-market-source.enabled`（环境变量 `PLATFORM_PLUGIN_MARKET_SOURCE_ENABLED`，默认开）。关闭时管理端点不注册、内置源不播种、公开 v2/legacy 端点不存在。
- 应用闸门：在「平台 → 能力管理」中启用。关闭后源行可仍存在但不生效：市场目录返回空，详情/安装/更新抛「插件市场源能力未启用」，公开端点停止服务。
- 存储目录：`yudream.platform.plugin.market-source.directory`（默认 `market-source`），必须独立于插件扫描目录（`directories`），否则发布物会被当作已安装插件发现。

## 2. 源类型

| 类型 | 用途 |
| --- | --- |
| `LOCAL` | 仅内置 `default`（本机插件市场）。进程内直读 `PUBLISHED` 发布物，无需 rootUrl，无需同步。 |
| `V2_API` | 正式交互式源。rootUrl 填对端 v2 源基址，如 `https://host/api/public/plugin-market`。 |
| `STATIC_INDEX` | legacy 静态 `index.json`。Nexus 或任意静态目录可作为此类源手动添加，不再是默认源。 |

创建自定义源禁止 `LOCAL`。内置源不可删除；存量部署若仍指向 Nexus，启动迁移会改写为 LOCAL 并清空残留 URL。

## 3. 公开社区页

能力开启后，公开站提供 Modrinth 式发现页（SiteChrome）：

- `/market`：搜索、分类/标签/作者/时间筛选、排序与分页卡片（图标、名称、版本、分类与标签、作者、下载量、更新时间）；
- `/market/:code`：详情、版本列表、匿名下载（计数 `$inc`）。

登录且具备 `platform:plugin-market-source:upload` 可从发现页发布。表单字段均可空：JAR 必填，分类/标签/许可证/兼容性/发布说明缺省以 `plugin.yml` 与服务端生成 descriptor 为准。发布后进入本机源（是否立刻对外可见取决于审核开关与跳过审核权限）。页脚展示当前站点 v2 源基址 `{origin}/api/public/plugin-market`，供其他实例添加为 `V2_API` 源。

后台拆成三个平台菜单（均映射能力 `plugin-market-source`，能力关闭则一起隐藏）：

| 菜单 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| 市场源管理 | `/platform/plugin-market-source` | `view` | 订阅源增删改、启停、同步、对外基址 |
| 插件发布 | `/platform/plugin-publish` | `upload` | 我的插件、升版上传、编辑分类/标签 |
| 发布审核 | `/platform/plugin-review` | `accept` | 待审/拒绝/下架，以及全局 `reviewRequired` 开关 |

存量库靠菜单扫描 `MISSING_ONLY` 补新节点；角色需给新菜单勾权限。

## 4. 发布插件

发布物元数据从 JAR 内 `plugin.yml` 自动解析（`name`/`version`/`main`/`displayName`/`description`/`depend`/`softdepend`）。服务端计算 SHA-256 并生成契约 descriptor（`mavenCoordinates` 兜底 `self-hosted:{code}:{version}`）。分类与标签是社区元数据，`plugin.yml` 不携带。

### 4.1 界面上传

「平台 → 插件发布」或公开 `/market` 发布入口，权限 `platform:plugin-market-source:upload`。发布说明可选、单行（对外契约禁止控制字符）。作者只能看到并改自己的发布物；持有 `accept` 或源管理 `edit`/`delete` 的人仍可处理全部。

内置分类清单（单值，可空）：`AI 与对话`、`支付与钱包`、`Minecraft`、`消息与社区`、`数据与看板`、`主题与皮肤`、`效率工具`、`其他`。标签自由填写：最多 10 个、单个 ≤24 字符、统一小写。

### 4.2 流水线发布（API Key）

1. 在「系统 → 安全中心 → API Key」创建 Key，权限勾选 `platform:plugin-market-source:upload`（Key 绑定创建者身份，发布可审计）；
2. 调用同一发布端点（`X-API-Key` 请求头，multipart 文件字段 `file`）：

```bash
curl --fail-with-body -X POST \
  "https://yudream.example.com/api/platform/plugin-market-source/publications" \
  -H "X-API-Key: yda_xxxx" \
  -F "file=@dist/plugins/example-plugin-1.0.0.jar" \
  -F "releaseNotes=首个发布版本" \
  -F "category=Minecraft" \
  -F "tags=chat,wiki" \
  -F 'metadata={"license":"MIT","compatibility":{"host":"^1.0.0"},"publisher":{"id":"yudream","name":"YuDream","url":"https://yudream.online","verified":true}}'
```

3. 插件仓可复用模板脚本 `ci/publish-to-market.sh` 与 `.gitlab-ci.yml.example` 中的 `publish:market` job（环境变量 `YUDREAM_MARKET_URL`、`YUDREAM_MARKET_API_KEY`，需配置为受保护、掩码 CI variables）。可选 `YUDREAM_MARKET_CATEGORY`、`YUDREAM_MARKET_TAGS`、`YUDREAM_MARKET_RELEASE_NOTES`、`YUDREAM_MARKET_METADATA`。

`metadata` 各字段均为可选：

| 字段 | 约束 |
| --- | --- |
| `publisher` | 可省略；提供时 `id/name/url/verified` 四项必填，url 必须为 HTTPS |
| `license` | SPDX 表达式（如 `MIT`、`Apache-2.0`） |
| `compatibility` | 键 `host`/`spi`/`frontendSdk`，值为 SemVer 区间（如 `^1.0.0`、`>=2.0.0 <3.0.0`）；只下发提供的键 |
| `category` / `tags` | 也可走独立表单字段；分类必须是内置清单值 |
| `releaseNotes`（表单字段） | 单行、≤4096 字符 |

### 4.3 审核、编辑与删除

「平台 → 发布审核」顶部的「发布需审核」开关对应能力配置键 `reviewRequired`（默认开）：

- 开启：界面上传与流水线发布均进入 `PENDING`，由持有 `platform:plugin-market-source:accept` 权限的管理员「通过/拒绝」；
- 关闭：发布直接 `PUBLISHED` 对外可见；
- 持有 `platform:plugin-market-source:publish`（跳过审核直接发布）的人上传一律 `PUBLISHED`，无视全局开关。不按用户存配置。

管理端列表支持 `mine`、`status` 与分页。无 `accept` 且无源管理 `edit`/`delete` 时，作者只能编辑/下架/删除自己的记录。

编辑可改分类/标签/展示元数据并重生成 descriptor，不重置审核状态，`code`/`pluginVersion`/`main`/`sha256` 不变。下架保留记录与 JAR；硬删除移除记录与文件，不可恢复。

## 5. 状态机与不可变发布物

```text
PENDING ──通过──▶ PUBLISHED ──下架──▶ REVOKED
   └────拒绝────▶ REJECTED
```

- `{code}@{pluginVersion}` 不可覆盖：重复发布同一版本返回 400，修复必须发布新版本；
- 拒绝与下架保留发布记录与 JAR 文件备查，仅停止对外下发；
- 下架/拒绝的版本不能恢复，需重新发布新版本。

## 6. 协议 v2（正式源协议）

基址 `/api/public/plugin-market/api/v2`。其他实例添加源时：类型选「v2 协议源」，地址填 `https://host/api/public/plugin-market`（不要带 `/api/v2`）。裸 JSON、HTTP 状态码表错（400/404），可选 `Authorization: Bearer`。仅 `PUBLISHED` 出现在目录中；能力关闭即停止服务。

| 端点 | 说明 |
| --- | --- |
| `GET /manifest` | `{"protocol":"yudream-market-v2","name","pluginCount"}` |
| `GET /categories` | `[{"code","name","count"}...]` 内置分类清单及计数 |
| `GET /tags?limit=30` | `[{"tag","count"}...]` 常用标签 top N |
| `GET /plugins?search=&categories=&tags=&authorId=&publishedAfter=&publishedBefore=&sort=newest\|downloads\|updated\|name&page=&size=` | 多维检索。`categories`/`tags` 逗号分隔（任一命中 OR），`authorId` 精确，时间 ISO-8601 区间；返回 `{"total","page","size","items":[...]}`，`size`≤100 |
| `GET /plugins/{code}` | summary + `versions`（SemVer 升序，末项为最新），每项含 `downloadPath`、`sha256`、`downloads` |
| `GET /plugins/{code}/versions/{version}/download` | JAR 流（attachment）+ 下载计数 |

列表项字段：`code`、`displayName`、`description`、`icon`、`category`、`tags`、`authorId`、`authorName`、`latestVersion`、`downloads`、`publishedAt`、`updatedAt`、`license`。长 ID 全程字符串。

消费端网关对 `V2_API` 分页拉取 `/plugins` 再取详情构造结构化快照；安装按 `downloadPath` 下载并校验 SHA-256。本机 `LOCAL` 源不走 HTTP，目录由进程内发布物分组生成。

## 7. legacy 静态索引

`schemaVersion=1` 静态目录仍可用，仅作兼容：

```text
GET /api/public/plugin-market/index.json
GET /api/public/plugin-market/{code}/index.json          # versions 按 SemVer 升序
GET /api/public/plugin-market/{code}/{version}/descriptor.json
GET /api/public/plugin-market/{code}/{version}/plugin.jar
```

其他实例若只能消费静态索引，可把 `https://你的域名/api/public/plugin-market/index.json` 添加为 `STATIC_INDEX` 源。相对引用按各 `index.json` 所在目录解析，且必须位于源根路径之内；JAR 下载强制 SHA-256。新社区与跨实例订阅应使用 v2。

## 8. 安全边界

- 公开 v2/legacy 端点匿名只读；发布/审核/跳过审核分别需要 `upload`/`accept`/`publish` 权限。源管理仍用 `view/create/edit/delete/run`。API Key 与账号身份都会记录到发布物的 `publisherUserId` 与通道；
- JAR 大小上限沿用 `yudream.platform.plugin.store-max-jar-bytes`（默认 100MB）；
- 存储目录独立于插件扫描目录；发布物文件路径由服务端数据库记录解析，URL 参数仅用于查询匹配，不参与路径拼接；
- 市场源 token 经主密钥加密存储，接口只返回是否已配置。
