package online.yudream.base.interfaces.platform.ai.assembler;

import online.yudream.base.application.platform.ai.cmd.AiModelDiscoveryCmd;
import online.yudream.base.application.platform.ai.dto.AiModelDiscoveryDTO;
import online.yudream.base.interfaces.platform.ai.request.AiModelDiscoveryRequest;
import online.yudream.base.interfaces.platform.ai.res.AiDiscoveredModelRes;
import online.yudream.base.interfaces.platform.ai.res.AiModelDiscoveryRes;

/** 端点模型发现的请求/DTO 边界转换。 */
public final class AiModelCatalogWebAssembler {

    private AiModelCatalogWebAssembler() {
    }

    public static AiModelDiscoveryCmd toCmd(AiModelDiscoveryRequest request) {
        AiModelDiscoveryCmd cmd = new AiModelDiscoveryCmd();
        cmd.setProviderCode(request.providerCode());
        cmd.setUsage(request.usage());
        cmd.setProtocol(request.protocol());
        cmd.setBaseUrl(request.baseUrl());
        cmd.setApiKey(request.apiKey());
        cmd.setProxyUrl(request.proxyUrl());
        return cmd;
    }

    public static AiModelDiscoveryRes toRes(AiModelDiscoveryDTO dto) {
        return AiModelDiscoveryRes.builder()
                .models(dto.getModels().stream()
                        .map(model -> AiDiscoveredModelRes.builder()
                                .code(model.getCode())
                                .name(model.getName())
                                .kind(model.getKind())
                                .build())
                        .toList())
                .listed(dto.getListed())
                .message(dto.getMessage())
                .build();
    }
}
