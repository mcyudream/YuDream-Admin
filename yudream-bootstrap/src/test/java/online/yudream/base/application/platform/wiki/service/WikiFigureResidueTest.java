package online.yudream.base.application.platform.wiki.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WikiFigureResidueTest {

    @Test void stripsFeishuFigureBlocksAndCollapsesBlankRuns() {
        String input = "# 使用指南\n\n<figure data-line=\"17\">上传皮肤截图.png<figcaption>上传皮肤截图.png</figcaption></figure>\n\n\n\n前往\"皮肤库\"上传皮肤。";
        String result = WikiIngestAppService.stripFigureResidue(input);
        assertThat(result).doesNotContain("<figure").doesNotContain("上传皮肤截图.png</figcaption>");
        assertThat(result).isEqualTo("# 使用指南\n\n前往\"皮肤库\"上传皮肤。");
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
