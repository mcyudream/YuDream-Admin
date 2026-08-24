package online.yudream.base.domain.platform.graph.service;

import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.valobj.GraphProjection;
import online.yudream.base.domain.platform.graph.valobj.GraphProjectionReplaceResult;
import online.yudream.base.domain.platform.graph.valobj.GraphProjectionView;
import online.yudream.base.domain.platform.graph.valobj.GraphProjectionViewRequest;

/** Dedicated gateway for versioned projection replacement and reads; never a general query gateway. */
public interface GraphProjectionGateway {

    GraphProjectionReplaceResult replaceProjection(GraphConnection connection, String pluginCode, GraphProjection projection);

    GraphProjectionView readProjection(GraphConnection connection, String pluginCode, GraphProjectionViewRequest request);
}
