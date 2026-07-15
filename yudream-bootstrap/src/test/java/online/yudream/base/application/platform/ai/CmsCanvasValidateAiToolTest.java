package online.yudream.base.application.platform.ai;

import online.yudream.base.application.platform.ai.service.CmsCanvasValidateAiTool;
import online.yudream.base.application.platform.ai.service.CmsCanvasAiTool;
import online.yudream.base.application.platform.ai.service.CmsCanvasAtomicAiTools;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.cms.repo.CmsBlockRepo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CmsCanvasValidateAiToolTest {

    private final CmsCanvasValidateAiTool tool = new CmsCanvasValidateAiTool();

    @Test
    void rejectsHtmlClassesWithoutCssCoverage() {
        var result = tool.execute(new AiAgentToolCall(
                CmsCanvasValidateAiTool.TOOL_NAME,
                Map.of(
                        "htmlContent", "<section class=\"yb-ai-hero yb-ai-card\"></section>",
                        "cssContent", ".yb-ai-hero { padding: 24px; }",
                        "jsContent", ""
                )
        ));

        assertThat(result.payload().get("valid")).isEqualTo(false);
        assertThat(result.payload().get("errors").toString()).contains("yb-ai-card");
    }

    @Test
    void acceptsCompleteHtmlCssAndInteractiveJavaScript() {
        var result = tool.execute(new AiAgentToolCall(
                CmsCanvasValidateAiTool.TOOL_NAME,
                Map.of(
                        "htmlContent", "<section class=\"yb-ai-tabs\" data-yb-tabs></section>",
                        "cssContent", ".yb-ai-tabs { display: grid; }",
                        "jsContent", """
                                const tabs = document.querySelector('[data-yb-tabs]');
                                const onClick = () => {};
                                tabs?.addEventListener('click', onClick);
                                window.__YU_CMS_REGISTER_CLEANUP__?.(() => tabs?.removeEventListener('click', onClick));
                                """
                )
        ));

        assertThat(result.payload().get("valid")).isEqualTo(true);
        assertThat(result.message()).contains("校验通过");
    }

    @Test
    void ignoresLockedChromeClassesButStillValidatesEditableHomeClasses() {
        var result = tool.execute(new AiAgentToolCall(
                CmsCanvasValidateAiTool.TOOL_NAME,
                Map.of(
                        "htmlContent", """
                                <header data-yb-chrome="header" class="site-layout-header">
                                  <div class="site-layout-header__bar"></div>
                                </header>
                                <main data-yb-home-content class="site-builder-home">
                                  <section class="yb-ai-hero"></section>
                                </main>
                                <footer data-yb-chrome="footer" class="site-layout-footer">
                                  <nav class="site-layout-footer__nav"></nav>
                                </footer>
                                """,
                        "cssContent", ".yb-ai-hero { min-height: 520px; }",
                        "jsContent", ""
                )
        ));

        assertThat(result.payload()).containsEntry("valid", true);
    }

    @Test
    void ignoresSystemAdminFrameClassesButStillValidatesEditableHomeClasses() {
        var result = tool.execute(new AiAgentToolCall(
                CmsCanvasValidateAiTool.TOOL_NAME,
                Map.of(
                        "htmlContent", """
                                <header data-yb-chrome="header" class="site-layout-header"></header>
                                <div data-yb-layout="ADMIN" class="site-layout-frame layout-admin">
                                  <aside data-yb-chrome="admin-sidebar" class="site-admin-sidebar"></aside>
                                  <div class="site-layout-content">
                                    <main data-yb-home-content class="site-builder-home">
                                      <section class="yb-ai-hero"></section>
                                    </main>
                                  </div>
                                </div>
                                <footer data-yb-chrome="footer" class="site-layout-copyright"></footer>
                                """,
                        "cssContent", ".yb-ai-hero { min-height: 520px; }",
                        "jsContent", ""
                )
        ));

        assertThat(result.payload()).containsEntry("valid", true);
    }

    @Test
    void rejectsLongRunningJavaScriptWithoutCleanup() {
        var result = tool.execute(new AiAgentToolCall(
                CmsCanvasValidateAiTool.TOOL_NAME,
                Map.of(
                        "htmlContent", "<section class=\"yb-ai-scene\"></section>",
                        "cssContent", ".yb-ai-scene { min-height: 320px; }",
                        "jsContent", "const frame = () => requestAnimationFrame(frame); requestAnimationFrame(frame);"
                )
        ));

        assertThat(result.payload().get("valid")).isEqualTo(false);
        assertThat(result.payload().get("errors").toString())
                .contains("window.__YU_CMS_REGISTER_CLEANUP__")
                .contains("cancelAnimationFrame");
    }

    @Test
    void rejectsStructuralCanvasPatchWithoutCss() {
        var canvasTool = new CmsCanvasAiTool();

        assertThatThrownBy(() -> canvasTool.execute(new AiAgentToolCall(
                CmsCanvasAiTool.TOOL_NAME,
                Map.of("action", "add-html", "htmlContent", "<section class=\"yb-ai-card\"></section>")
        )))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("cssContent");
    }

    @Test
    void rejectsAtomicBlockWithoutCss() {
        var blockTool = new CmsCanvasAtomicAiTools(mock(CmsBlockRepo.class)).cmsCanvasAddBlockTool();

        assertThatThrownBy(() -> blockTool.execute(new AiAgentToolCall(
                "cms.canvas.block.add",
                Map.of("htmlContent", "<section class=\"yb-ai-card\"></section>")
        )))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("cssContent");
    }

    @Test
    void rejectsCanvasAddHtmlWhenCssDoesNotCoverEveryHtmlClass() {
        var canvasTool = new CmsCanvasAiTool();

        assertThatThrownBy(() -> canvasTool.execute(new AiAgentToolCall(
                CmsCanvasAiTool.TOOL_NAME,
                Map.of(
                        "action", "add-html",
                        "htmlContent", "<section class=\"yb-ai-service-card yb-ai-service-card--mc\"></section>",
                        "cssContent", ".yb-ai-service-card { display: grid; }"
                )
        )))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("yb-ai-service-card--mc");
    }

    @Test
    void rejectsAtomicBlockWhenCssDoesNotCoverEveryHtmlClass() {
        var blockTool = new CmsCanvasAtomicAiTools(mock(CmsBlockRepo.class)).cmsCanvasAddBlockTool();

        assertThatThrownBy(() -> blockTool.execute(new AiAgentToolCall(
                "cms.canvas.block.add",
                Map.of(
                        "htmlContent", "<section class=\"yb-ai-community yb-ai-community__content\"></section>",
                        "cssContent", ".yb-ai-community { padding: 48px; }"
                )
        )))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("yb-ai-community__content");
    }
}
