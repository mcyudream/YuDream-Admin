package online.yudream.base.plugin.spi.system.graph;
/** Read input for a versioned plugin projection in a logical table. */
public record PluginGraphProjectionViewRequest(String tableCode, String namespace, String versionId, int nodePage, int nodeSize, int relationshipPage, int relationshipSize) {
    public PluginGraphProjectionViewRequest { tableCode=tableCode==null?"":tableCode.trim(); namespace=namespace==null?"":namespace.trim(); versionId=versionId==null?"":versionId.trim(); }
}
