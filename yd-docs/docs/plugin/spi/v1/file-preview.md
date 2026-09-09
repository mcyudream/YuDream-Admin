# 文件预览 FilePreviewSpi

> SPI v1 · 当前源码 2.24.0 · 包 `online.yudream.base.plugin.spi.system.preview`

平台文件预览能力端口。能力编码 `file-preview`，项目闸门 `PLATFORM_FILE_PREVIEW_ENABLED`。默认实现表示宿主未提供该能力（`enabled()` 恒 false、预览恒为 `NONE`）。产品行为见 [文件预览](/features/file-preview)。

| 端口 | 获取方式 | 用途 |
|---|---|---|
| `PluginFilePreviewService` | `context.filePreview()` 或 `framework().filePreview()` | 签发签名地址、给出 KKFILE / DIRECT / NONE 决策 |

## PluginFilePreviewService

| 方法 | 签名 | 说明 |
|---|---|---|
| `enabled` | `default boolean enabled()` | kkFileView 是否已启用且配置完整 |
| `kkFileViewBaseUrl` | `default String kkFileViewBaseUrl()` | 当前生效的 kkFileView 服务地址 |
| `officePreviewType` | `default String officePreviewType()` | office 预览类型；空串表示不追加参数 |
| `tokenTtlSeconds` | `default long tokenTtlSeconds()` | 签名文件地址时效，默认 1800 |
| `maxPreviewSizeMb` | `default long maxPreviewSizeMb()` | 预览大小上限（MB），默认 200 |
| `callbackBaseUrl` | `default String callbackBaseUrl()` | 回源基址；空串表示从请求推导 |
| `kkFileViewUrl` | `default String kkFileViewUrl(String absoluteFileUrl)` | 包装为 kkFileView iframe 绝对地址 |
| `signedFileUrl` | `default String signedFileUrl(String pluginCode, String objectKey, String filename)` | 签发插件文件的短时效公开地址（`/api/` 开头） |
| `preview` | `default PluginPreviewInfo preview(String pluginCode, PluginPreviewFile file)` | 为插件文件构建预览 |
| `previewExternal` | `default PluginPreviewInfo previewExternal(String absoluteFileUrl, String ext, long size)` | 为外部绝对地址构建预览 |

## DTO

`PluginPreviewFile(objectKey, filename, contentType, size)`：插件文件存储中的对象描述。

`PluginPreviewInfo(mode, url, message)`：

| mode | 含义 |
|---|---|
| `KKFILE` | `url` 为 kkFileView iframe 绝对地址 |
| `DIRECT` | `url` 为可直读地址（宿主内为 `/api/` 相对地址，前端用 `sdk.files.assetUrl(url)` 解析） |
| `NONE` | 不可预览，`message` 为面向用户的提示 |

```java
PluginPreviewInfo info = context.filePreview().preview(context.pluginCode(),
        new PluginPreviewFile("docs/report.docx", "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", size));
if (PluginPreviewInfo.MODE_NONE.equals(info.mode())) {
    // 展示下载入口
}
```

## 注意事项

- 先判断 `enabled()` 再调用业务方法；能力未启用时图片等直读格式仍可能返回 `DIRECT`。
- 插件不要自行对接 kkFileView 或签发预览令牌。
- 长 ID 与 objectKey 全程字符串；objectKey 不得含 `..`。
