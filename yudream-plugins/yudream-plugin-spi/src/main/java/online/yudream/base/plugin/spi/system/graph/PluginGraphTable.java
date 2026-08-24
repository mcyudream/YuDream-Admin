package online.yudream.base.plugin.spi.system.graph;

/** A logical graph table authorized for the calling plugin. It contains no deployment information or credentials. */
public record PluginGraphTable(String code, String name, String description) {
    public PluginGraphTable {
        code = code == null ? "" : code.trim();
        name = name == null ? "" : name.trim();
        description = description == null ? "" : description.trim();
    }
}
