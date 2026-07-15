package online.yudream.base.application.platform.ai;

import online.yudream.base.application.platform.ai.service.CmsCanvasAiTool;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CmsCanvasAiToolTest {

    private final CmsCanvasAiTool tool = new CmsCanvasAiTool();

    @Test
    void rejectsAllChromeTargetsBecauseCanvasIsTheCombinedHomeSurface() {
        assertThrows(BizException.class, () -> tool.execute(new AiAgentToolCall(
                CmsCanvasAiTool.TOOL_NAME,
                Map.of("target", "header", "action", "set-html", "htmlContent", "<div>changed</div>")
        )));
        assertThrows(BizException.class, () -> tool.execute(new AiAgentToolCall(
                CmsCanvasAiTool.TOOL_NAME,
                Map.of("target", "footer", "action", "set-css", "cssContent", ".yb-chrome-footer { color: red; }")
        )));
    }
}
