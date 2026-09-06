package online.yudream.base.application.platform.milky.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.milky.port.OfficialQqBotWebhookGateway;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OfficialQqBotWebhookAppService {
    private final MilkyConnectionRepo connectionRepo;
    private final CapabilityAppService capabilityAppService;
    private final OfficialQqBotWebhookGateway webhookGateway;

    public Object handle(Long connectionId, String rawBody, String signature, String timestamp) {
        capabilityAppService.ensureEnabled("milky", "QQ 消息平台");
        MilkyConnection connection = connectionRepo.findById(connectionId)
                .filter(MilkyConnection::isEnabled)
                .filter(MilkyConnection::official)
                .orElseThrow(() -> new BizException("官方机器人连接不存在或未启用"));
        return webhookGateway.handle(connection, rawBody, signature, timestamp);
    }
}
