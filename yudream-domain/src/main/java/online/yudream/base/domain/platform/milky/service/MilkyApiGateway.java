package online.yudream.base.domain.platform.milky.service;

import online.yudream.base.domain.platform.milky.model.MilkyModels.Context;

/**
 * The complete Milky API surface is reachable through this protocol-neutral raw invocation port.
 * Typed higher-level operations are deliberately composed on top of it to avoid duplicating the
 * protocol's expanding endpoint list in every consumer.
 */
public interface MilkyApiGateway {
    Object invoke(Context context, String api, Object body);
}
