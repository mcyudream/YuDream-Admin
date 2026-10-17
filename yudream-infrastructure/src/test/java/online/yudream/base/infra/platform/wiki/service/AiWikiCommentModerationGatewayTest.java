package online.yudream.base.infra.platform.wiki.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.valobj.WikiCommentModerationVerdict;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 评论审核解析：模型输出必须是可判定的 JSON；拿不到判定一律抛异常（由应用层回落人工审核），
 * 不能把解析失败当成「通过」或「拒绝」——那样等于用坏输出替人做决定。
 * 基础设施单测不使用 mock 框架：这里用最小手写桩。
 */
class AiWikiCommentModerationGatewayTest {

    @Test
    void parsesApprovedAndRejectedVerdictsWithReason() {
        AiWikiCommentModerationGateway gateway = gateway("{\"approved\": true, \"reason\": \"与页面主题相关\"}");
        WikiCommentModerationVerdict approved = gateway.review("p", "m", "库", "页", "", "评论");
        assertTrue(approved.approved());
        assertEquals("与页面主题相关", approved.reason());

        AiWikiCommentModerationGateway rejected = gateway("{\"approved\": false, \"reason\": \"广告引流\"}");
        WikiCommentModerationVerdict verdict = rejected.review("p", "m", "库", "页", "引用", "加微信");
        assertFalse(verdict.approved());
        assertEquals("广告引流", verdict.reason());
    }

    @Test
    void toleratesFencedJsonAndCapsReasonLength() {
        AiWikiCommentModerationGateway gateway = gateway("```json\n{\"approved\": true, \"reason\": \"" + "长".repeat(200) + "\"}\n```");
        WikiCommentModerationVerdict verdict = gateway.review("p", "m", "库", "页", "", "评论");
        assertTrue(verdict.approved());
        assertEquals(WikiCommentModerationVerdict.MAX_REASON_LENGTH, verdict.reason().length());
    }

    @Test
    void unusableModelOutputNeverBecomesAVerdict() {
        // 缺字段、类型不对、不是 JSON：三种都必须是异常（回落人工审核），不能被当成判定结果
        for (String output : List.of(
                "{\"reason\": \"没有 approved\"}",
                "{\"approved\": \"yes\", \"reason\": \"字符串布尔\"}",
                "我觉得这条评论还行",
                "")) {
            assertThrows(BizException.class, () -> gateway(output).review("p", "m", "库", "页", "", "评论"), output);
        }
    }

    @Test
    void refusesToReviewWithoutModelOrWithoutEnabledAiCapability() {
        assertThrows(BizException.class, () -> gateway("{\"approved\": true}").review("p", " ", "库", "页", "", "评论"));
        AiWikiCommentModerationGateway noCapability = new AiWikiCommentModerationGateway(
                emptyCapability(), provider(new StubGenerationGateway("{\"approved\": true}")),
                new com.fasterxml.jackson.databind.ObjectMapper());
        assertThrows(BizException.class, () -> noCapability.review("p", "m", "库", "页", "", "评论"));
    }

    private static AiWikiCommentModerationGateway gateway(String output) {
        AiGenerationGateway generation = new StubGenerationGateway(output);
        return new AiWikiCommentModerationGateway(aiCapability(), provider(generation),
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private static CapabilityModuleRepo aiCapability() {
        CapabilityModule module = new CapabilityModule();
        module.setCode("ai");
        module.setEnabled(true);
        module.setConfig(Map.of("providers", "[]"));
        return capabilityRepo(module);
    }

    private static CapabilityModuleRepo emptyCapability() {
        return capabilityRepo(null);
    }

    /** 仓库不是函数式接口：手写最小桩（只实现 findByCode，其余抛不支持）。 */
    private static CapabilityModuleRepo capabilityRepo(CapabilityModule module) {
        return new CapabilityModuleRepo() {
            @Override
            public CapabilityModule save(CapabilityModule value) {
                throw new UnsupportedOperationException("测试桩不支持保存");
            }

            @Override
            public Optional<CapabilityModule> findByCode(String code) {
                return module != null && module.getCode().equals(code) ? Optional.of(module) : Optional.empty();
            }

            @Override
            public List<CapabilityModule> findAll() {
                return module == null ? List.of() : List.of(module);
            }
        };
    }

    private static ObjectProvider<AiGenerationGateway> provider(AiGenerationGateway gateway) {
        return new ObjectProvider<>() {
            @Override
            public AiGenerationGateway getObject() {
                return gateway;
            }
        };
    }

    /** 最小生成桩：只回放固定文本，记录最后一次请求便于断言提示词装配。 */
    private static final class StubGenerationGateway implements AiGenerationGateway {
        private final String output;

        private StubGenerationGateway(String output) {
            this.output = output;
        }

        @Override
        public AiGenerationResult generate(AiGenerationRequest request) {
            return new AiGenerationResult("", output, "", "", "", "", "", List.of(), List.of(), null);
        }
    }
}
