package online.yudream.base.plugin.spi.system.graph;
import java.util.List;
/** Complete replacement input for a plugin-scoped projection in an authorized logical table. */
public record PluginGraphProjectionRequest(String tableCode, String namespace, String versionId, List<PluginGraphProjectionNode> nodes, List<PluginGraphProjectionRelationship> relationships) {
    public PluginGraphProjectionRequest { tableCode=tableCode==null?"":tableCode.trim(); namespace=namespace==null?"":namespace.trim(); versionId=versionId==null?"":versionId.trim(); nodes=nodes==null?List.of():List.copyOf(nodes); relationships=relationships==null?List.of():List.copyOf(relationships); }
    @Deprecated public PluginGraphProjectionRequest(String tableCode,String namespace,List<PluginGraphProjectionNode> nodes,List<PluginGraphProjectionRelationship> relationships){this(tableCode,namespace,"default",nodes,relationships);}
}
