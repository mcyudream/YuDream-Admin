package online.yudream.base.infra.platform.milky.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.milky.model.MilkyModels.Context;
import online.yudream.base.domain.platform.milky.service.MilkyApiGateway;
import online.yudream.base.infra.platform.milky.official.OfficialQqBotApiAdapter;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 共享出站端口：Milky 走原 HTTP API；官方连接走 OpenAPI 适配器。上层只看见同一个 invoke。
 */
@Service
@Primary
@RequiredArgsConstructor
public class RoutingMilkyApiGateway implements MilkyApiGateway {
    private final ReactorMilkyApiGateway milky;
    private final OfficialQqBotApiAdapter official;

    @Override
    public Object invoke(Context context, String api, Object body) {
        if (context != null && context.official()) {
            return official.invoke(context, api, body);
        }
        return milky.invoke(context, api, body);
    }
}
