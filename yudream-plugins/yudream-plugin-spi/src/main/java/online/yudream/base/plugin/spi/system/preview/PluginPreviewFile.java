package online.yudream.base.plugin.spi.system.preview;

/**
 * 待预览的插件文件描述。
 *
 * @param objectKey   插件文件存储中的对象键（{@code files(pluginCode)} 命名空间内）
 * @param filename    展示文件名，用于 Content-Disposition 与 kkFileView 标题；可为空（取对象键末段）
 * @param contentType 文件 MIME，可为空（由对象存储元数据兜底）
 * @param size        字节数，用于预览大小上限判断
 */
public record PluginPreviewFile(String objectKey, String filename, String contentType, long size) {
}
