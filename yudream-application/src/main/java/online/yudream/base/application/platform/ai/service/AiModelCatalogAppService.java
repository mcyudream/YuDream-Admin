package online.yudream.base.application.platform.ai.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.assembler.AgentModelCatalogParser;
import online.yudream.base.application.platform.agent.dto.AgentModelDTO;
import online.yudream.base.application.platform.ai.cmd.AiModelDiscoveryCmd;
import online.yudream.base.application.platform.ai.dto.AiDiscoveredModelDTO;
import online.yudream.base.application.platform.ai.dto.AiModelDiscoveryDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiModelDiscoveryGateway;
import online.yudream.base.domain.platform.ai.valobj.AiModelDiscoveryQuery;
import online.yudream.base.domain.platform.ai.valobj.AiModelDiscoveryResult;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 端点模型目录服务：按协议请求远端模型列表，供管理端「获取模型」一键登记。
 * <p>
 * 属于平台能力闸门管控的外部调用：能力未启用时拒绝，避免绕过双闸门直接外联。
 */
@Service
@RequiredArgsConstructor
public class AiModelCatalogAppService {

    private static final String AI_CAPABILITY = "ai";

    private final CapabilityModuleRepo capabilityModuleRepo;
    private final ObjectProvider<AiModelDiscoveryGateway> discoveryGateway;
    private final AgentModelCatalogParser modelCatalogParser;

    /** AI/CMS 的模型选择只依赖 AI 能力，不要求开启 Agent 应用编排或 Wiki。 */
    @Transactional(readOnly = true)
    public List<AgentModelDTO> availableModels() {
        Map<String, String> config = capabilityModuleRepo.findByCode(AI_CAPABILITY)
                .filter(module -> module.enabled())
                .map(module -> module.getConfig() == null ? Map.<String, String>of() : module.getConfig())
                .orElseThrow(() -> new BizException("AI 能力未启用，请先在平台能力中启用 AI 助手"));
        return modelCatalogParser.parse(config).stream()
                .filter(model -> "chat".equals(model.kind()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AiModelDiscoveryDTO discoverModels(AiModelDiscoveryCmd cmd) {
        AiModelDiscoveryGateway gateway = discoveryGateway.getIfAvailable();
        if (gateway == null) {
            throw new BizException("AI 能力未启用，无法获取模型；请先在平台能力中启用 AI 助手");
        }
        Map<String, String> config = capabilityModuleRepo.findByCode(AI_CAPABILITY)
                .filter(module -> module.enabled())
                .map(module -> module.getConfig() == null ? Map.<String, String>of() : module.getConfig())
                .orElseThrow(() -> new BizException("AI 能力未启用，无法获取模型；请先在平台能力中启用 AI 助手"));
        AiModelDiscoveryResult result = gateway.discover(new AiModelDiscoveryQuery(
                cmd.getProviderCode(),
                cmd.getUsage(),
                cmd.getProtocol(),
                cmd.getBaseUrl(),
                cmd.getApiKey(),
                cmd.getProxyUrl(),
                config
        ));
        List<AiDiscoveredModelDTO> models = result.models().stream()
                .map(model -> AiDiscoveredModelDTO.builder()
                        .code(model.code())
                        .name(model.name())
                        .kind(model.kind())
                        .build())
                .toList();
        return AiModelDiscoveryDTO.builder()
                .models(models)
                .listed(result.listed())
                .message(result.message())
                .build();
    }
}
