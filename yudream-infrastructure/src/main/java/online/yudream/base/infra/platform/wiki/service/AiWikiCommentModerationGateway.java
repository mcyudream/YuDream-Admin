package online.yudream.base.infra.platform.wiki.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.service.WikiCommentModerationGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiCommentModerationVerdict;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 评论机器审核：用知识库配置的问答模型判定一条评论能否直接公开。
 * <p>
 * 判定口径保守——只有「明显可公开」才给通过，其余一律交给人工：
 * 与页面无关的广告引流、辱骂攻击、违法违规、纯灌水都会被拒绝；
 * 模型输出无法解析时抛异常，由应用层记为「审核不可用」并回落人工，绝不阻断读者提交。
 */
@Service
@RequiredArgsConstructor
public class AiWikiCommentModerationGateway implements WikiCommentModerationGateway {

    private static final String PROMPT = """
            你是知识库的评论审核员，判断一条读者评论能否立即公开。
            通过（approved=true）的条件：与页面主题相关，且不含广告引流、辱骂攻击、违法有害内容或纯灌水。
            拿不准时一律给 false（交人工复核），不要自行解释或改写评论。
            只返回 JSON，不要 Markdown，不要多余文字：
            {"approved": true 或 false, "reason": "不超过 40 字的判断理由"}
            """;

    private final CapabilityModuleRepo capabilityModuleRepo;
    private final ObjectProvider<AiGenerationGateway> generationGateway;
    private final ObjectMapper objectMapper;

    @Override
    public WikiCommentModerationVerdict review(String providerCode, String modelCode, String spaceName,
                                               String pageTitle, String quoted, String content) {
        if (providerCode == null || providerCode.isBlank() || modelCode == null || modelCode.isBlank()) {
            throw new BizException("未配置审核用的问答模型");
        }
        Map<String, String> config = capabilityModuleRepo.findByCode("ai")
                .filter(module -> module.enabled()).map(module -> module.getConfig())
                .orElseThrow(() -> new BizException("AI 能力未启用"));
        String prompt = new StringBuilder()
                .append("知识库：").append(text(spaceName)).append('\n')
                .append("页面：").append(text(pageTitle)).append('\n')
                .append("引用原文：").append(quoted == null || quoted.isBlank() ? "（无）" : quoted).append('\n')
                .append("评论正文：").append(text(content))
                .toString();
        String output = generationGateway.getObject()
                .generate(new AiGenerationRequest(PROMPT, prompt, null, providerCode, modelCode, config)).summary();
        return parse(output);
    }

    private WikiCommentModerationVerdict parse(String output) {
        String json = stripFence(output);
        if (json.isBlank()) {
            throw new BizException("评论审核没有返回内容");
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode approved = node.get("approved");
            if (approved == null || !approved.isBoolean()) {
                throw new BizException("评论审核结果缺少 approved 字段");
            }
            String reason = node.path("reason").asText("");
            return approved.asBoolean()
                    ? WikiCommentModerationVerdict.approve(reason)
                    : WikiCommentModerationVerdict.reject(reason);
        }
        catch (BizException error) {
            throw error;
        }
        catch (Exception error) {
            throw new BizException("评论审核结果无法解析：" + error.getMessage());
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String stripFence(String value) {
        return value == null ? "" : value.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
    }
}
