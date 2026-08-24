package online.yudream.base.plugin.spi.system.graph;

/**
 * @deprecated Since 2.12.0 use {@link PluginGraphTable}. Kept only as a source-compatibility DTO;
 * it does not identify or configure a physical Neo4j database.
 */
@Deprecated(since = "2.12.0", forRemoval = true)
public record PluginGraphConnection(String code, String name, String description) {
    public PluginGraphConnection { code=code==null?"":code.trim(); name=name==null?"":name.trim(); description=description==null?"":description.trim(); }
}
