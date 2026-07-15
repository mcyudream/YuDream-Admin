package online.yudream.base.application.platform.ai;

import online.yudream.base.application.platform.ai.service.CmsChromeAiTool;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CmsChromeAiToolTest {

    private final CmsChromeAiTool tool = new CmsChromeAiTool();

    @Test
    void validatesTheCombinedChromeStructure() {
        AiAgentToolResult result = tool.execute(new AiAgentToolCall(
                CmsChromeAiTool.TOOL_NAME,
                Map.of("action", "validate", "target", "both", "htmlContent", "<header data-yb-chrome=\"header\"></header><footer data-yb-chrome=\"footer\"></footer>")));

        assertThat(result.payload()).containsEntry("valid", true);
        assertThat(result.payload()).containsEntry("target", "both");
    }

    @Test
    void convertsChromeStyleChangesIntoHomeCanvasStylePatch() {
        AiAgentToolResult result = tool.execute(new AiAgentToolCall(
                CmsChromeAiTool.TOOL_NAME,
                Map.of("action", "set-styles", "target", "header", "selector", ".site-layout-header", "styles", Map.of("color", "red"))));

        assertThat(result.payload()).containsEntry("target", "home");
        assertThat(result.payload()).containsEntry("selector", ".site-layout-header");
        assertThat(result.action()).isEqualTo("set-styles");
    }

    @Test
    void defaultsSingleChromeStyleToItsFixedCanvasNode() {
        AiAgentToolResult result = tool.execute(new AiAgentToolCall(
                CmsChromeAiTool.TOOL_NAME,
                Map.of("action", "set-styles", "target", "footer", "styles", Map.of("color", "red"))));

        assertThat(result.payload()).containsEntry("selector", "[data-yb-chrome=\"footer\"]");
    }

    @Test
    void rejectsStructuralChromeActions() {
        assertThatThrownBy(() -> tool.execute(new AiAgentToolCall(
                CmsChromeAiTool.TOOL_NAME,
                Map.of("action", "set-html", "target", "both"))))
                .isInstanceOf(BizException.class);
    }
}
