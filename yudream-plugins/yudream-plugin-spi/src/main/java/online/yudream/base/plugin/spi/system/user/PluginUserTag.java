package online.yudream.base.plugin.spi.system.user;

/**
 * 系统用户标签。按 namespace 隔离，插件只能替换自己命名空间下的标签。
 * 人员管理只读展示 label；code 用于同命名空间内区分用途（如 school / status）。
 */
public record PluginUserTag(String namespace, String code, String label) {

    public PluginUserTag {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("标签命名空间不能为空");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("标签编码不能为空");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("标签名称不能为空");
        }
        namespace = namespace.trim();
        code = code.trim();
        label = label.trim();
    }
}
