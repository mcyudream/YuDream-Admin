package online.yudream.base.plugin.spi.system.user;

/** Plugin-owned field displayed by personnel management. */
public record PluginUserField(String namespace, String code, String label, String value) {

    public PluginUserField {
        if (namespace == null || namespace.isBlank() || code == null || code.isBlank()) {
            throw new IllegalArgumentException("扩展字段命名空间和编码不能为空");
        }
        namespace = namespace.trim();
        code = code.trim();
        label = label == null ? code : label.trim();
        value = value == null ? "" : value.trim();
    }
}
