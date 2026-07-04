package online.yudream.base.interfaces.platform.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.service.AiAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.ai.assembler.AiWebAssembler;
import online.yudream.base.interfaces.platform.ai.request.CmsPageGenerateRequest;
import online.yudream.base.interfaces.platform.ai.res.CmsPageGenerateRes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/ai")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiController {

    private final AiAppService aiAppService;

    @PostMapping("/cms/pages/generate")
    @PermissionRegister(code = "platform:ai:generate", name = "AI 生成页面", module = "平台能力", desc = "使用 AI 为 CMS 生成页面草稿")
    public Result<CmsPageGenerateRes> generateCmsPage(@Valid @RequestBody CmsPageGenerateRequest request) {
        return Result.ok(AiWebAssembler.toRes(aiAppService.generateCmsPage(AiWebAssembler.toCmd(request))));
    }
}
