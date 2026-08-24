package online.yudream.base.plugin.spi.system.graph;

import java.util.List;

/** Host-managed logical graph tables and projections scoped to the calling plugin. */
public interface PluginGraphService {
    List<PluginGraphTable> tables();

    /** Resolves the calling plugin's unique active authorized graph-table binding without accessing projections. */
    PluginGraphBindingStatus bindingStatus();

    PluginGraphProjectionResult replaceProjection(PluginGraphProjectionRequest request);
    PluginGraphProjectionView readProjection(PluginGraphProjectionViewRequest request);

    /** Replaces a projection in the host's unique active table authorized for this plugin. */
    PluginGraphProjectionResult replaceProjection(PluginGraphProjection projection);

    /** Replaces a complete graph snapshot in the host's unique active table authorized for this plugin. */
    default PluginGraphProjectionResult replaceCompleteProjection(PluginGraphCompleteProjection projection) {
        return PluginGraphProjectionResult.failure(PluginGraphErrorCode.CAPABILITY_UNAVAILABLE, "Complete graph projection is unavailable");
    }

    /** Reads a projection in the host's unique active table authorized for this plugin. */
    PluginGraphProjectionView readProjection(PluginGraphProjectionReadRequest request);

    static PluginGraphService unavailable() {
        return new PluginGraphService() {
            @Override public List<PluginGraphTable> tables() { return List.of(); }
            @Override public PluginGraphBindingStatus bindingStatus() { return PluginGraphBindingStatus.unavailable(PluginGraphErrorCode.CAPABILITY_UNAVAILABLE, "Graph capability is unavailable"); }
            @Override public PluginGraphProjectionResult replaceProjection(PluginGraphProjectionRequest request) { return PluginGraphProjectionResult.failure(PluginGraphErrorCode.CAPABILITY_UNAVAILABLE, "Graph capability is unavailable"); }
            @Override public PluginGraphProjectionView readProjection(PluginGraphProjectionViewRequest request) { return PluginGraphProjectionView.failure(PluginGraphErrorCode.CAPABILITY_UNAVAILABLE, "Graph capability is unavailable", request); }
            @Override public PluginGraphProjectionResult replaceProjection(PluginGraphProjection projection) { return PluginGraphProjectionResult.failure(PluginGraphErrorCode.CAPABILITY_UNAVAILABLE, "Graph capability is unavailable"); }
            @Override public PluginGraphProjectionResult replaceCompleteProjection(PluginGraphCompleteProjection projection) { return PluginGraphProjectionResult.failure(PluginGraphErrorCode.CAPABILITY_UNAVAILABLE, "Graph capability is unavailable"); }
            @Override public PluginGraphProjectionView readProjection(PluginGraphProjectionReadRequest request) { return PluginGraphProjectionView.failure(PluginGraphErrorCode.CAPABILITY_UNAVAILABLE, "Graph capability is unavailable", request); }
        };
    }
}
