package online.yudream.base.domain.platform.wiki.valobj;

import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiFrontmatterTest {

    @Test
    void roundTripPreservesStructuredFieldsAndBody() {
        WikiFrontmatter frontmatter = WikiFrontmatter.of("实体页", WikiPageType.ENTITY,
                List.of("/papers/a.pdf", "/papers/b.pdf"), List.of("概念X"), List.of("标签1"),
                "这是一个实体页摘要", "正文内容\n\n[[概念X]]");

        String markdown = frontmatter.fullMarkdown();
        WikiFrontmatter parsed = WikiFrontmatter.parse(markdown);

        assertEquals("实体页", parsed.title());
        assertEquals(WikiPageType.ENTITY, parsed.pageType());
        assertEquals(List.of("/papers/a.pdf", "/papers/b.pdf"), parsed.sources());
        assertEquals(List.of("概念X"), parsed.related());
        assertEquals(List.of("标签1"), parsed.tags());
        assertEquals("这是一个实体页摘要", parsed.summary());
        assertTrue(parsed.body().contains("[[概念X]]"));
    }

    @Test
    void parseWithoutFrontmatterFallsBackToBody() {
        WikiFrontmatter parsed = WikiFrontmatter.parse("只有正文，没有 frontmatter");
        assertEquals("只有正文，没有 frontmatter", parsed.bodyOnly());
        assertEquals(WikiPageType.CONCEPT, parsed.pageType());
    }

    @Test
    void bodyOnlyExcludesFrontmatter() {
        WikiFrontmatter frontmatter = WikiFrontmatter.of("T", WikiPageType.CONCEPT, List.of(), List.of(), List.of(), "s", "body");
        assertEquals("body", frontmatter.bodyOnly());
    }
}
