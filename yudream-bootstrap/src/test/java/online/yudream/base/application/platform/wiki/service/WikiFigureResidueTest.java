package online.yudream.base.application.platform.wiki.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WikiFigureResidueTest {

    @Test void stripsFeishuFigureBlocksAndCollapsesBlankRuns() {
        String input = "# 使用指南\n\n<figure data-line=\"17\">上传皮肤截图.png<figcaption>上传皮肤截图.png</figcaption></figure>\n\n\n\n前往\"皮肤库\"上传皮肤。";
        String result = WikiIngestAppService.stripFigureResidue(input);
        // 解包裹语义：标签删除、内层文字保留
        assertThat(result).doesNotContain("<figure").doesNotContain("figcaption");
        assertThat(result).contains("前往\"皮肤库\"上传皮肤。");
    }

    @Test void unwrapsEscapedEntityFigureWrappersKeepingInnerImage() {
        String input = "<p data-line=\"34\">（1）打开安装程序。</p>\n&lt;figure data-line=\"36\"&gt;<img src=\"/api/files/1/content\" alt=\"\">&lt;/figure&gt;\n<p data-line=\"38\">（2）选择安装路径。</p>";
        String result = WikiIngestAppService.stripFigureResidue(input);
        assertThat(result).doesNotContain("&lt;figure").doesNotContain("&lt;/figure&gt;");
        assertThat(result).contains("<img src=\"/api/files/1/content\" alt=\"\">");
        assertThat(result).contains("<p data-line=\"34\">");
    }

    @Test void stripsUnclosedFigureOpenTags() {
        assertThat(WikiIngestAppService.stripFigureResidue("<figure data-line=\"3\">残段"))
                .doesNotContain("<figure");
    }

    @Test void keepsCleanMarkdownUntouched() {
        String clean = "| 新皮肤站 |\n|:---:|\n| ![截图](/api/files/1/content '截图') |\n\n正文。";
        assertThat(WikiIngestAppService.stripFigureResidue(clean)).isEqualTo(clean);
    }

    @Test void toleratesNullAndBlankAndUnclosedTags() {
        // null 归一为空串：下游 truncate/拼接不需要处理 null
        assertThat(WikiIngestAppService.stripFigureResidue(null)).isEmpty();
        assertThat(WikiIngestAppService.stripFigureResidue("")).isEmpty();
        // 未闭合的 figure 开标签也应剥离，避免残留字面文本
        assertThat(WikiIngestAppService.stripFigureResidue("<figure data-line=\"3\">残段"))
                .doesNotContain("<figure");
    }
}
