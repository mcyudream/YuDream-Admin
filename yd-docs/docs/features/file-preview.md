# 文件预览

文件预览（能力 code：`file-preview`）由宿主统一提供、插件消费。插件通过 SPI `PluginFilePreviewService` 获取预览地址，不再各自实现签名、kkFileView 对接与配置存储。

管理入口：**平台能力 > 文件预览**。项目闸门环境变量 `PLATFORM_FILE_PREVIEW_ENABLED`（默认 `true`）；运行闸门由管理后台启用开关决定。插件端口见 [FilePreviewSpi](/plugin/spi/v1/file-preview)。

## 预览决策

1. 浏览器可原生渲染的类型（图片 / 视频 / 音频 / PDF / 文本，且未超大小限制）→ `DIRECT`：返回平台签名公开地址 `/api/public/preview/file/{token}/{filename}`，前端用 `sdk.files.assetUrl()` 解析。
2. 其余格式且 kkFileView 已就绪（启用且配置了服务地址）→ `KKFILE`：返回 `onlinePreview?url=...` 绝对地址，前端 iframe 渲染。
3. 其余 → `NONE`，调用方展示提示与下载入口。

能力配置键：`baseUrl`（kkFileView 浏览器可达地址）、`callbackBaseUrl`（回源基址，留空按请求推导）、`officePreviewType`（默认 `pdf`）、`tokenTtlSeconds`（默认 `1800`）、`maxPreviewSizeMb`（默认 `200`）。

## 查看链路

```text
浏览器 iframe   http(s)://<站点>/kkfileview/onlinePreview?url=...
     │  nginx location ^~ /kkfileview/（剥离前缀，超时放宽到 600s）
     ▼
kkfileview 容器 http://kkfileview:8012/onlinePreview?url=...
     │  按 url 参数回源拉取文件字节
     ▼
backend 容器    http://backend:8080/api/public/preview/file/{token}/{filename}
```

`docker-compose.yml` 已包含 `kkfileview` 服务（默认镜像 tag `5.0.2`）。标准部署下：

- 浏览器地址填 `http(s)://<站点>/kkfileview`（frontend nginx 同源反代）；
- 回源基址填 `http://backend:8080`，`KK_TRUST_HOST` 包含 `backend`；
- `KK_BASE_URL` 保持 `default`：nginx 已注入 `X-Base-Url`。

能力页「测试」由后端容器发起；compose 为 backend 配置了 `FILE_PREVIEW_KKFILEVIEW_INTERNAL_URL=http://kkfileview:8012`，主地址探测失败时用内网地址兜底。

## 安全基线

- 签名端点只认 HMAC 令牌 + 过期时间；objectKey 逐段校验，拒绝 `..`。
- `trust.host` 校验被预览文件 URL 的主机（防 SSRF），生产保持白名单精确匹配。
- 不建议经 backend 代理预览流量：大文件经 JVM 中转会放大带宽与内存压力。

更完整的部署与镜像同步说明见仓库 `docs/platform/file-preview.md`。
