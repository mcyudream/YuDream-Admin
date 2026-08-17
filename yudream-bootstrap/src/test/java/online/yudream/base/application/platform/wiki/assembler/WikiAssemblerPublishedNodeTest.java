package online.yudream.base.application.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.dto.WikiNodeDTO;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIndexStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WikiAssemblerPublishedNodeTest {

    @Test
    void publicPageWithoutFrontmatterUsesVersionTitleInsteadOfDraftTitle() {
        WikiNodeDTO stable = WikiNodeDTO.builder()
                .id("1")
                .title("草稿新标题")
                .slug("page")
                .nodeType(WikiNodeType.PAGE)
                .publishedVersionId("10")
                .children(List.of())
                .build();
        WikiPageVersion version = WikiPageVersion.builder()
                .id(10L)
                .nodeId(1L)
                .title("旧发布标题")
                .markdown("旧版本正文")
                .build();

        WikiNodeDTO dto = WikiAssembler.publishedNode(stable, version);

        assertThat(dto).isNotNull();
        assertThat(dto.getTitle()).isEqualTo("旧发布标题");
        assertThat(dto.getBody()).isEqualTo("旧版本正文");
    }

    @Test
    void publicPageDtoUsesOnlyPublishedVersionDisplayFields() {
        WikiNodeDTO stable = WikiNodeDTO.builder()
                .id("1")
                .title("草稿标题")
                .slug("page")
                .path("/guide/page")
                .nodeType(WikiNodeType.PAGE)
                .sort(0)
                .markdown("---\ntitle: 草稿标题\ntype: concept\n---\n草稿正文")
                .body("草稿正文")
                .publishedVersionId("10")
                .indexStatus(WikiIndexStatus.READY)
                .children(List.of())
                .pageType("concept")
                .sources(List.of("草稿源"))
                .related(List.of("草稿关联"))
                .tags(List.of("草稿标签"))
                .summary("草稿摘要")
                .build();
        WikiPageVersion version = WikiPageVersion.builder()
                .id(10L)
                .nodeId(1L)
                .title("发布标题")
                .markdown("---\ntitle: 发布标题\ntype: entity\nsummary: 发布摘要\nsources:\n  - 发布源\nrelated:\n  - 发布关联\ntags:\n  - 发布标签\n---\n发布正文")
                .build();

        WikiNodeDTO dto = WikiAssembler.publishedNode(stable, version);

        assertThat(dto).isNotNull();
        assertThat(dto.getTitle()).isEqualTo("发布标题");
        assertThat(dto.getBody()).isEqualTo("发布正文");
        assertThat(dto.getSummary()).isEqualTo("发布摘要");
        assertThat(dto.getSources()).containsExactly("发布源");
        assertThat(dto.getRelated()).containsExactly("发布关联");
        assertThat(dto.getTags()).containsExactly("发布标签");
        assertThat(dto.getPageType()).isEqualTo("entity");
        assertThat(dto.getId()).isEqualTo("1");
        assertThat(dto.getSlug()).isEqualTo("page");
        assertThat(dto.getPublishedVersionId()).isEqualTo("10");
    }
}
