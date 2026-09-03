# 文件预览（平台能力）

平台内置的统一文件预览能力，由宿主提供、插件消费。插件通过 SPI `PluginFilePreviewService`
（`online.yudream.base.plugin.spi.system.preview`）获取预览地址，不再各自实现签名、kkFileView 对接与设置存储。

- 管理入口：**平台能力 > 文件预览**（能力编码 `file-preview`，权限 `platform:capability:view/config/enable/disable/test`）。
- 双闸门控制：部署侧环境变量 `PLATFORM_FILE_PREVIEW_ENABLED`（默认 `true`）决定能力是否出现；
  运行侧由管理后台启用开关决定运行时是否生效，配置随能力入库（`capability_module`）。
- 能力配置键：`baseUrl`（kkFileView 浏览器可达地址）、`callbackBaseUrl`（回源基址，留空按请求推导）、
  `officePreviewType`（默认 `pdf`）、`tokenTtlSeconds`（默认 `1800`）、`maxPreviewSizeMb`（默认 `200`）。
- 运行时实现：`PluginFilePreviewFrameworkService`（yudream-infrastructure）经能力 Provider
  （`FilePreviewCapabilityProvider`）读取配置与启停；签名凭据从 `YUDREAM_CREDENTIAL_KEY` 派生。

## 预览决策链

1. 浏览器可原生渲染的类型（图片/视频/音频/PDF/文本，且未超大小限制）→ `DIRECT`：返回平台签名公开地址
   `/api/public/preview/file/{token}/{filename}`，前端用 `sdk.files.assetUrl()` 解析。
   直读优先可避免 kk 页面跨域回源被浏览器拦截、以及 kk 模板兼容性问题。
2. 其余格式且 kkFileView 已就绪（启用且配置了服务地址）→ `KKFILE`：返回 kkFileView 的 `onlinePreview?url=...` 绝对地址，前端 iframe 渲染。
3. 其余 → `NONE`，调用方展示提示与下载入口。

签名公开端点 `PublicFilePreviewController` 支持匿名访问与 Range 请求，令牌为 HMAC-SHA256 签名，
默认有效期 1800 秒、单文件上限 200MB（均可在能力配置中调整）。

## 查看链路与反向代理

浏览器不直连 kkFileView 端口，统一走 frontend 镜像内置 nginx 的同源反代（`docker/nginx.conf`）：

```text
浏览器 iframe   http(s)://<站点>/kkfileview/onlinePreview?url=...
     │  nginx location ^~ /kkfileview/（剥离前缀，转换超时放宽到 600s）
     ▼
kkfileview 容器 http://kkfileview:8012/onlinePreview?url=...
     │  kkFileView 服务端按 url 参数回源拉取文件字节
     ▼
backend 容器    http://backend:8080/api/public/preview/file/{token}/{filename}
```

要点：

- `trust.host`（`KK_TRUST_HOST`）校验的是**被预览文件 URL 的主机**（即回源地址的 host，防 SSRF），
  与浏览器从哪个站点打开预览页无关；未配置时拒绝一切预览请求。compose 默认
  `backend,localhost,127.0.0.1` 对应回源地址 `http://backend:8080`。
- kkFileView 服务端生成页面资源与跳转地址时使用 `base.url`（`KK_BASE_URL`，compose 默认 `default`）。
  `default` 表示按**浏览器实际请求**自动推导（见 kk 的 `BaseUrlFilter`），且反代注入的 `X-Base-Url`
  请求头优先于该配置——frontend nginx 的 `/kkfileview/` 块已注入 `$scheme://$http_host/kkfileview`，
  因此标准部署下 kk 容器**无需任何环境相关配置**；仅当 kk 前面是没有注入该头的其他反代时，
  才需把 `KK_BASE_URL` 显式覆盖为浏览器可达的前缀地址。
- 不需要也不建议经 backend 代理预览流量：签名公开端点本来就是为 kkFileView 直接回源设计的，
  大文件经 JVM 中转只会放大带宽与内存压力。
- compose 中 8012 端口映射仅用于本机调试，生产可移除；nginx 反代配置烘在 frontend 镜像里，
  需重新构建发布 frontend 镜像后才生效。
- 能力页「测试」由**后端容器**发起：`baseUrl` 填浏览器地址（`http(s)://<站点>/kkfileview`）时，
  后端容器未必能直连（典型如本机 `localhost` 部署）。compose 已为 backend 配置
  `FILE_PREVIEW_KKFILEVIEW_INTERNAL_URL=http://kkfileview:8012`（对应配置项
  `file.preview.kkfileview.internal-url`，仅环境变量、不入库），主地址探测失败时自动用内网地址兜底，
  避免同源反代部署下的误报。

## kkFileView 镜像

镜像发布到与 backend/frontend 相同的命名空间：`registry.yudream.online/yudream/yudreamadmin/kkfileview:<version>`。

首选直接同步官方镜像（脚本内置国内镜像站优先、Docker Hub 兜底）：

```bash
docker login registry.yudream.online   # GitLab 容器仓库，凭据不进仓库
sh docker/kkfileview/push.sh           # 默认 5.0.2
sh docker/kkfileview/push.sh 5.0.2     # 显式指定版本
```

官方镜像不可用（架构、网络、需定制字体/配置）时，用本仓 Dockerfile 从源码构建：

```bash
MODE=build sh docker/kkfileview/push.sh 5.0.2
```

自构建镜像与官方 `kkfileview-base` 对齐：Ubuntu 24.04 + OpenJDK 21 JRE + `libreoffice-nogui` + 文泉驿/中文字体，
运行 kkFileView `server` 模块的 Spring Boot fat jar，暴露 8012。

## 部署与配置对应关系

`docker-compose.yml` 已包含 `kkfileview` 服务（默认端口 8012，watchtower 托管，资源上限 2C/2G）：

```bash
docker compose pull kkfileview && docker compose up -d kkfileview
```

两个地址方向不同，配置时务必区分：

| 配置 | 位置 | 谁访问谁 |
| --- | --- | --- |
| kkFileView 服务地址（能力配置 `baseUrl`） | 仅平台能力页 | 浏览器 → kkFileView（iframe 加载） |
| 回源基址（能力配置 `callbackBaseUrl`） | 仅平台能力页 | kkFileView 容器 → 宿主签名端点（拉文件字节） |
| `KK_TRUST_HOST` | compose 环境变量 | kkFileView 白名单校验回源地址的 host |
| `KK_BASE_URL` | compose 环境变量（默认 `default`，通常无需设置） | kkFileView 生成预览页内资源地址 |

典型 compose 内网部署：回源基址填 `http://backend:8080`，`KK_TRUST_HOST` 包含 `backend`；
kkFileView 服务地址填 `http(s)://<站点>/kkfileview`（frontend nginx 同源反代）。
`KK_BASE_URL` 保持 `default`：nginx 已注入 `X-Base-Url`，kk 会自动生成带前缀的正确资源地址；
本地开发浏览器直连 `127.0.0.1:8012` 时同样无需设置，`default` 按请求自动推导。

## 安全基线

- 签名端点只认 HMAC 令牌 + 过期时间，不暴露 objectKey 以外的身份；objectKey 逐段校验，拒绝 `..` 与空段。
- kkFileView v5.0.2 默认已禁用首页上传（`file.upload.disable = true` 硬编码，无对应环境变量）。
- `trust.host` 校验被预览文件 URL 的主机（回源 host，防 SSRF），不配时拒绝一切预览请求；
  生产环境保持白名单精确匹配，不要用 `*`。
- 文件删除接口建议设置 `KK_DELETE_PASSWORD`，见 kkFileView v5.0.2 安全公告。
- 预览缓存默认 `jdk` 堆内缓存；多实例部署时切 `redis` 并配置 `KK_SPRING_REDISSON_*`。

## 验证

1. `curl http://<kkfileview>:8012` 返回首页即服务正常；能力页「测试」做同样的事（后端容器发起）。
   经 nginx 反代验证：`curl http://<站点>/kkfileview/` 应返回同一首页。
2. 在「平台能力 > 文件预览」配置 `baseUrl` 并启用后，在物料库等消费方打开一个 Office 文件，
   应出现 kkFileView iframe 预览；能力停用后同一文件回退为下载提示，图片仍为 `DIRECT`。
3. 图片应走 `DIRECT`：检查 `<img>` 地址为 `/api/public/preview/file/...` 且能匿名打开；篡改或过期 token 应返回 404。
