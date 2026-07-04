package online.yudream.base.application.platform.ai.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.assembler.AiAssembler;
import online.yudream.base.application.platform.ai.cmd.CmsPageGenerateCmd;
import online.yudream.base.application.platform.ai.dto.CmsPageGenerateDTO;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiAppService {

    private static final String CAPABILITY_CODE = "ai";

    private final CapabilityAppService capabilityAppService;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final ObjectProvider<AiGenerationGateway> aiGenerationGatewayProvider;

    @Transactional(readOnly = true)
    public CmsPageGenerateDTO generateCmsPage(CmsPageGenerateCmd cmd) {
        capabilityAppService.ensureEnabled(CAPABILITY_CODE, "AI");
        if (!StringUtils.hasText(cmd.getPrompt())) {
            throw new BizException("生成需求不能为空");
        }
        Map<String, String> config = capabilityModuleRepo.findByCode(CAPABILITY_CODE)
                .map(module -> module.getConfig() == null ? Map.<String, String>of() : module.getConfig())
                .orElse(Map.of());
        AiGenerationRequest request = new AiGenerationRequest(systemPrompt(), userPrompt(cmd), config);
        AiGenerationGateway gateway = aiGenerationGatewayProvider.getIfAvailable();
        if (gateway == null) {
            throw new BizException("AI 能力未在当前项目配置中启用");
        }
        return AiAssembler.toDTO(gateway.generate(request));
    }

    private String systemPrompt() {
        return """
                你是 YuDream CMS 页面构建 Agent。只返回一个 JSON 对象，不要 Markdown 代码块，不要解释。
                JSON 字段必须包含：title, summary, htmlContent, cssContent, builderProjectJson, markdownContent。
                htmlContent 只返回页面主体内容，不要 html/head/body/script，不要系统导航栏或 footer。
                cssContent 只写作用于 htmlContent 的 scoped 风格，类名使用 yb-ai- 前缀。
                builderProjectJson 可以为空字符串；如果无法生成 GrapesJS project JSON，请让 htmlContent/cssContent 足够完整。
                页面视觉要现代、留白克制、响应式，不要使用外部脚本。
                """;
    }

    private String userPrompt(CmsPageGenerateCmd cmd) {
        return """
                站点：%s
                页面标题：%s
                页面类型：%s
                风格偏好：%s
                生成需求：%s
                """.formatted(
                defaultText(cmd.getSiteName(), "YuDream"),
                defaultText(cmd.getTitle(), "未命名页面"),
                defaultText(cmd.getPageType(), "通用内容页"),
                defaultText(cmd.getStyle(), "清爽、专业、可读性高"),
                cmd.getPrompt().trim()
        );
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
