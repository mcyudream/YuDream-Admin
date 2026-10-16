package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import online.yudream.base.domain.platform.wiki.valobj.WikiFrontmatter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiGeneratedPagePolicyTest {

    @Test
    void allowsOverwriteForUnpublishedGeneratedDraft() {
        String generated = generatedMarkdown("正文");

        assertTrue(WikiGeneratedPagePolicy.mayOverwriteDraft(generated, null));
    }

    @Test
    void allowsOverwriteForGeneratedDraftEqualToPublishedVersion() {
        String generated = generatedMarkdown("正文");

        assertTrue(WikiGeneratedPagePolicy.mayOverwriteDraft(generated, generated));
    }

    @Test
    void blocksOverwriteWhenDraftChangedAfterPublish() {
        String generated = generatedMarkdown("正文");
        String handEdited = generatedMarkdown("正文") + "\n人工补充的结论";

        assertFalse(WikiGeneratedPagePolicy.mayOverwriteDraft(handEdited, generated));
    }

    @Test
    void blocksOverwriteWhenGeneratedDraftDiffersFromPublishedVersion() {
        String previousGeneration = generatedMarkdown("旧正文");
        String currentDraft = generatedMarkdown("新正文");

        assertFalse(WikiGeneratedPagePolicy.mayOverwriteDraft(currentDraft, previousGeneration));
    }

    @Test
    void blocksOverwriteForManualPageWithoutGeneratedMarker() {
        String manual = WikiFrontmatter.of("人工页", WikiPageType.CONCEPT, List.of(), List.of(), List.of(), "摘要", "正文")
                .fullMarkdown();

        assertFalse(WikiGeneratedPagePolicy.mayOverwriteDraft(manual, null));
        assertFalse(WikiGeneratedPagePolicy.mayOverwriteDraft(manual, manual));
    }

    @Test
    void blocksOverwriteForLegacyDraftWithoutOriginMarker() {
        String legacy = "---\ntitle: 旧页面\ntype: concept\n---\n旧正文";

        assertFalse(WikiGeneratedPagePolicy.mayOverwriteDraft(legacy, null));
        assertFalse(WikiGeneratedPagePolicy.mayOverwriteDraft(legacy, legacy));
    }

    @Test
    void allowsCreatingContentOnEmptyDraft() {
        assertTrue(WikiGeneratedPagePolicy.mayOverwriteDraft(null, null));
    }

    private static String generatedMarkdown(String body) {
        return WikiFrontmatter.of("生成页", WikiPageType.CONCEPT, List.of("/a.pdf"), List.of(), List.of("source"),
                        "生成摘要", body)
                .withOrigin(WikiFrontmatter.ORIGIN_LLM)
                .fullMarkdown();
    }
}
