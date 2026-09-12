# 自托管插件市场源

「插件市场源」能力（code `plugin-market-source`，能力中心「插件分发」分组）让每个 YuDream 实例拥有两种身份：作为**订阅方**添加多个市场源并安装其中的插件（见[插件市场与第三方上架](./marketplace)第 6 节），以及作为**供给方**对外提供插件分发——本文描述供给方：插件作者无需 Nexus 和 CI 权限，即可向本机市场源发布新版本，其他实例把它添加为市场源后自动获得更新。

## 1. 启用能力

- 项目闸门：`yudream.platform.capabilities.plugin-market-source.enabled`（环境变量 `PLATFORM_PLUGIN_MARKET_SOURCE_ENABLED`，默认开）；
- 应用闸门：在「平台 → 能力管理」中启用插件市场源；关闭后发布/审核用例全部拒绝，公开目录端点停止服务；
- 存储目录：`yudream.platform.plugin.market-source.directory`（默认 `market-source`），必须独立于插件扫描目录（`directories`），否则发布物会被当作已安装插件发现。

## 2. 发布插件

发布物元数据从 JAR 内 `plugin.yml` 自动解析（`name`/`version`/`main`/`displayName`/`description`/`depend`/`softdepend`），无需手写 descriptor；服务端计算 SHA-256 并生成符合契约的 descriptor（`mavenCoordinates` 兜底为 `self-hosted:{code}:{version}`）。

### 2.1 界面上传

「平台 → 市场源管理 → 发布管理 → 上传插件」需要 `platform:plugin-market-source:upload` 权限。发布说明为可选、单行（对外契约禁止控制字符）；发布者记录为当前登录用户。

### 2.2 流水线发布（API Key）

1. 在「系统 → 安全中心 → API Key」创建 Key，权限勾选 `platform:plugin-market-source:upload`（Key 绑定创建者身份，发布可审计）；
2. 调用同一发布端点（`X-API-Key` 请求头，multipart 文件字段 `file`）：

```bash
curl --fail-with-body -X POST \
  "https://yudream.example.com/api/platform/plugin-market-source/publications" \
  -H "X-API-Key: yda_xxxx" \
  -F "file=@dist/plugins/example-plugin-1.0.0.jar" \
  -F "releaseNotes=首个发布版本" \
  -F 'metadata={"license":"MIT","compatibility":{"host":"^1.0.0"},"publisher":{"id":"yudream","name":"YuDream","url":"https://yudream.online","verified":true}}'
```

3. 插件仓可直接复用模板脚本 `ci/publish-to-market.sh` 与 `.gitlab-ci.yml.example` 中的 `publish:market` job（环境变量 `YUDREAM_MARKET_URL`、`YUDREAM_MARKET_API_KEY`，需配置为受保护、掩码 CI variables）。

`metadata` 各字段均为可选：

| 字段 | 约束 |
| --- | --- |
| `publisher` | 可省略；提供时 `id/name/url/verified` 四项必填，url 必须为 HTTPS |
| `license` | SPDX 表达式（如 `MIT`、`Apache-2.0`） |
| `compatibility` | 键 `host`/`spi`/`frontendSdk`，值为 SemVer 区间（如 `^1.0.0`、`>=2.0.0 <3.0.0`）；只下发提供的键 |
| `releaseNotes`（表单字段） | 单行、≤4096 字符 |

### 2.3 审核开关

「发布管理」顶部的「发布需审核」开关对应能力配置键 `reviewRequired`（默认开）：

- 开启：界面上传与流水线发布均进入 `PENDING`，由持有 `platform:plugin-market-source:accept` 权限的管理员「通过/拒绝」；
- 关闭：发布直接 `PUBLISHED` 对外可见——这就是「非 Nexus 用户自动发布新版本」的形态，配合流水线发布可做到推送即上架。

## 3. 状态机与不可变发布物

```text
PENDING ──通过──▶ PUBLISHED ──下架──▶ REVOKED
   └────拒绝────▶ REJECTED
```

- `{code}@{version}` 不可覆盖：重复发布同一版本返回 400，修复必须发布新版本；
- 拒绝与下架保留发布记录与 JAR 文件备查，仅停止对外下发；
- 下架/拒绝的版本不能恢复，需重新发布新版本。

## 4. 对外契约

开启能力后，本机在 `/api/public/plugin-market/` 暴露与官方市场完全同构的 `schemaVersion=1` 只读目录（匿名可读）：

```text
GET /api/public/plugin-market/index.json                            # {"schemaVersion":1,"plugins":[{"code":"demo","index":"demo/index.json"}]}
GET /api/public/plugin-market/{code}/index.json                     # versions 按 SemVer 升序（消费端取最后一项为最新版）
GET /api/public/plugin-market/{code}/{version}/descriptor.json      # 发布时生成并经契约校验
GET /api/public/plugin-market/{code}/{version}/plugin.jar           # 流式下发，Content-Type application/java-archive
```

descriptor 内的 `jar.url` 等相对引用按各 `index.json` 所在目录解析，且必须位于源根路径之内；JAR 下载强制 SHA-256 校验。仅 `PUBLISHED` 状态的发布物出现在目录中。

其他实例接入：在「市场源管理」中把 `https://你的域名/api/public/plugin-market/index.json` 添加为市场源即可；消费端强制 HTTPS，自托管源需经 HTTPS 反向代理暴露。

## 5. 安全边界

- 公开端点匿名只读；发布/审核/下架分别需要 `upload`/`accept`/`delete` 权限，API Key 与账号身份都会记录到发布物的 `publisherUserId` 与通道；
- JAR 大小上限沿用 `yudream.platform.plugin.store-max-jar-bytes`（默认 100MB）；
- 存储目录独立于插件扫描目录；发布物文件路径由服务端数据库记录解析，URL 参数仅用于查询匹配，不参与路径拼接。
