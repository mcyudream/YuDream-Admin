package online.yudream.base.interfaces.platform.ai.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.service.AiModelCatalogAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.ai.assembler.AiModelCatalogWebAssembler;
import online.yudream.base.interfaces.platform.ai.request.AiModelDiscoveryRequest;
import online.yudream.base.interfaces.platform.ai.res.AiModelDiscoveryRes;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 端点模型目录：为能力配置页提供「获取模型」，复用与能力测试相同的平台运维权限。
 */
@RestController
@RequestMapping("/api/platform/ai/endpoints")
@RequiredArgsConstructor
public class AiEndpointController {

    private final AiModelCatalogAppService aiModelCatalogAppService;

    @PostMapping("/discover-models")
    @PermissionRegister(code = "platform:capability:test", name = "测试平台能力", module = "平台能力", desc = "探测平台能力端点（含 AI 端点模型列表）")
    public Result<AiModelDiscoveryRes> discoverModels(@RequestBody AiModelDiscoveryRequest request) {
        return Result.ok(AiModelCatalogWebAssembler.toRes(
                aiModelCatalogAppService.discoverModels(AiModelCatalogWebAssembler.toCmd(request))));
    }
}
