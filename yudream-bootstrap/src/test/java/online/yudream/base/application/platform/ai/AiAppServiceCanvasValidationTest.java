package online.yudream.base.application.platform.ai.service;

import online.yudream.base.application.platform.ai.service.AiAppService;
import online.yudream.base.application.platform.ai.service.CmsCanvasValidateAiTool;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAppServiceCanvasValidationTest {

    @Test
    void doesNotFailCompletedCanvasWorkWhenTheModelOmitsFinalValidationTool() {
        assertThatCode(() -> AiAppService.ensureCanvasValidationPassed(List.of(
                new AiAgentToolResult("cms.canvas.patch", "add-html", "permission", "区块已追加", Map.of())
        ))).doesNotThrowAnyException();
    }

    @Test
    void stillRejectsAnExplicitFailedCanvasValidation() {
        assertThatThrownBy(() -> AiAppService.ensureCanvasValidationPassed(List.of(
                new AiAgentToolResult(
                        CmsCanvasValidateAiTool.TOOL_NAME,
                        "validate",
                        "permission",
                        "画布完整性校验未通过",
                        Map.of("valid", false, "errors", List.of("yb-ai-card 缺少 CSS"))
                )
        )))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("yb-ai-card 缺少 CSS");
    }
}
