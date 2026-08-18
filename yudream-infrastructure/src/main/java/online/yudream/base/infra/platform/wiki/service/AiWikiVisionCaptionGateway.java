package online.yudream.base.infra.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.wiki.service.WikiVisionCaptionGateway;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 复用平台 AI 网关的 vision 能力，为抽取出的图片生成事实性 caption。
 */
@Service
@RequiredArgsConstructor
public class AiWikiVisionCaptionGateway implements WikiVisionCaptionGateway {

    private static final String SYSTEM_PROMPT =
            "你是一个严谨的视觉描述助手。请用中文准确、客观地描述图片内容，只陈述可见事实，"
                    + "不臆测、不评价、不补充图片中没有的信息。输出一段不超过 200 字的连续描述。";

    private final AiGenerationGateway generation;

    @Override
    public String caption(String providerCode, String modelCode, Map<String, String> config, String imageDataUrl) {
        if (imageDataUrl == null || imageDataUrl.isBlank()) {
            throw new BizException("图片内容不能为空");
        }
        AiGenerationRequest request = new AiGenerationRequest(
                SYSTEM_PROMPT,
                "请描述这张图片。",
                imageDataUrl,
                providerCode,
                modelCode,
                config
        );
        AiGenerationResult result = generation.generate(request);
        String caption = result == null ? null : result.summary();
        if (caption == null || caption.isBlank()) {
            throw new BizException("视觉模型未返回图片描述");
        }
        return caption.trim();
    }
}
