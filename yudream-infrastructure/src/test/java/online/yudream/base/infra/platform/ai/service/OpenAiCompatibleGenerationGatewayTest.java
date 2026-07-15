package online.yudream.base.infra.platform.ai.service;

import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiCompatibleGenerationGatewayTest {

    @Test
    void preservesFinalModelTextWhenToolResultsExist() {
        var result = OpenAiCompatibleGenerationGateway.toResult(
                "已抓取参考页面，并据此完成分析。",
                List.of(new AiAgentToolResult("web.fetch", "fetch", "permission", "网页参考内容已抓取。", Map.of()))
        );

        assertEquals("已抓取参考页面，并据此完成分析。", result.summary());
        assertEquals(1, result.toolResults().size());
    }
}
