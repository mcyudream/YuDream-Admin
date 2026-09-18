package online.yudream.base.plugin.spi.http;

/**
 * multipart/form-data 请求中的一个 part（字段或文件）。
 * 插件通过 {@link PluginHttpRequest#parts()} 读取；字段 part 的 filename 为 null。
 */
public record PluginHttpPart(
        String name,
        String filename,
        String contentType,
        byte[] data
) {
    public PluginHttpPart {
        data = data == null ? new byte[0] : data;
    }

    /** 是否为上传文件 part（带 filename）。 */
    public boolean isFile() {
        return filename != null;
    }

    /** 以 UTF-8 读取字段 part 内容。 */
    public String text() {
        return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }
}
