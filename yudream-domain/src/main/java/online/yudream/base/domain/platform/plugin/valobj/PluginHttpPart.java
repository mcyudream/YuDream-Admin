package online.yudream.base.domain.platform.plugin.valobj;

/**
 * multipart/form-data 请求中的一个 part（字段或文件）。
 * 领域层载体；派发到插件时由网关映射为 SPI 的 PluginHttpPart。
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

    public boolean isFile() {
        return filename != null;
    }
}
