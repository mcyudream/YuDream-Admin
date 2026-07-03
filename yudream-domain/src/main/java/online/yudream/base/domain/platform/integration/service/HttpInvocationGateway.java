package online.yudream.base.domain.platform.integration.service;

import online.yudream.base.domain.platform.integration.aggregate.HttpConnector;
import online.yudream.base.domain.platform.integration.valobj.HttpInvocationResult;

import java.util.Map;

public interface HttpInvocationGateway {

    HttpInvocationResult invoke(HttpConnector connector, Map<String, String> headers, Map<String, String> queryParams, String body);
}
