package online.yudream.base.interfaces.platform.wiki.assembler;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.wiki.cmd.WikiCommentCreateCmd;
import online.yudream.base.application.platform.wiki.dto.WikiCommentAnchorDTO;
import online.yudream.base.application.platform.wiki.dto.WikiCommentDTO;
import online.yudream.base.interfaces.platform.wiki.request.WikiCommentAnchorRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiCommentCreateRequest;
import online.yudream.base.interfaces.platform.wiki.res.WikiCommentRes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评论接口层装配：请求只映射客户端锚点草稿（引用文本 + 扁平定位提示），
 * 服务端版本/哈希/身份字段不进入请求模型；公开响应 JSON 不暴露空间/作者身份，
 * 锚点版本身份与状态和 anchor 平级、状态为小写线协议名。
 */
class WikiCommentWebAssemblerTest {

    @Test
    void createMapsFlatClientAnchorDraft() {
        WikiCommentAnchorRequest anchor = new WikiCommentAnchorRequest();
        anchor.setExact("快速开始");
        anchor.setPrefix("欢迎来到");
        anchor.setSuffix("页面。");
        anchor.setBlockOrdinal(4);
        anchor.setBlockType("tableCell");
        anchor.setTableRow(1);
        anchor.setTableCell(2);
        anchor.setStartOffset(3);
        anchor.setEndOffset(8);
        WikiCommentCreateRequest request = new WikiCommentCreateRequest();
        request.setContent("内容");
        request.setAnchor(anchor);

        WikiCommentCreateCmd cmd = WikiCommentWebAssembler.create("demo", 20L, request);

        assertThat(cmd.getSpaceSlug()).isEqualTo("demo");
        assertThat(cmd.getNodeId()).isEqualTo(20L);
        assertThat(cmd.getAnchor().getExact()).isEqualTo("快速开始");
        assertThat(cmd.getAnchor().getPrefix()).isEqualTo("欢迎来到");
        assertThat(cmd.getAnchor().getSuffix()).isEqualTo("页面。");
        assertThat(cmd.getAnchor().getBlockOrdinal()).isEqualTo(4);
        assertThat(cmd.getAnchor().getBlockType()).isEqualTo("tableCell");
        assertThat(cmd.getAnchor().getTableRow()).isEqualTo(1);
        assertThat(cmd.getAnchor().getTableCell()).isEqualTo(2);
        assertThat(cmd.getAnchor().getStartOffset()).isEqualTo(3);
        assertThat(cmd.getAnchor().getEndOffset()).isEqualTo(8);
    }

    @Test
    void requestModelsHaveNoServerOwnedIdentityOrVersionFields() {
        assertThat(fieldNames(WikiCommentAnchorRequest.class))
                .doesNotContain("versionId", "revision", "contentHash", "spaceId", "nodeId", "authorUserId",
                        "authorName", "anchorState");
        assertThat(fieldNames(WikiCommentCreateRequest.class))
                .doesNotContain("authorUserId", "authorName", "spaceId", "status", "versionId");
    }

    @Test
    void toResMapsFlatAnchorScopeAndNestedReplies() {
        WikiCommentAnchorDTO anchor = WikiCommentAnchorDTO.builder()
                .exact("快速开始")
                .prefix("欢迎来到")
                .suffix("页面。")
                .blockOrdinal(1)
                .blockType("paragraph")
                .startOffset(0)
                .endOffset(4)
                .build();
        WikiCommentDTO reply = WikiCommentDTO.builder()
                .id("6").nodeId("20").content("回复").anchor(anchor)
                .versionId("200").revision("3").contentHash("hash-1").anchorState("active")
                .replies(List.of()).build();
        WikiCommentDTO root = WikiCommentDTO.builder()
                .id("5").nodeId("20").content("根评论").anchor(anchor)
                .versionId("200").revision("3").contentHash("hash-1").anchorState("active")
                .replies(List.of(reply)).build();

        WikiCommentRes res = WikiCommentWebAssembler.toRes(root);

        assertThat(res.getAnchor().getExact()).isEqualTo("快速开始");
        assertThat(res.getAnchor().getBlockOrdinal()).isEqualTo(1);
        assertThat(res.getAnchor().getBlockType()).isEqualTo("paragraph");
        assertThat(res.getAnchor().getStartOffset()).isZero();
        assertThat(res.getAnchor().getEndOffset()).isEqualTo(4);
        assertThat(res.getVersionId()).isEqualTo("200");
        assertThat(res.getRevision()).isEqualTo("3");
        assertThat(res.getContentHash()).isEqualTo("hash-1");
        assertThat(res.getAnchorState()).isEqualTo("active");
        assertThat(res.getReplies()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo("6");
            assertThat(item.getAnchorState()).isEqualTo("active");
        });
    }

    @Test
    void publicResJsonOmitsSpaceAuthorIdentityAndUnanchoredFields() throws Exception {
        WikiCommentDTO anchored = WikiCommentDTO.builder()
                .id("5")
                .nodeId("20")
                .authorType("USER")
                .authorName("小明")
                .content("内容")
                .status("PUBLISHED")
                .anchor(WikiCommentAnchorDTO.builder().exact("快速开始").blockType("paragraph").build())
                .versionId("200").revision("1").contentHash("hash-1").anchorState("active")
                .replies(List.of())
                .build();
        WikiCommentDTO legacy = WikiCommentDTO.builder()
                .id("6").nodeId("20").content("历史评论").status("PUBLISHED").replies(List.of()).build();

        ObjectMapper mapper = new ObjectMapper();
        String anchoredJson = mapper.writeValueAsString(WikiCommentWebAssembler.toRes(anchored));
        String legacyJson = mapper.writeValueAsString(WikiCommentWebAssembler.toRes(legacy));

        assertThat(anchoredJson).doesNotContain("spaceId", "authorUserId", "reviewedByUserId");
        assertThat(anchoredJson)
                .contains("\"anchorState\":\"active\"", "\"versionId\":\"200\"", "\"exact\":\"快速开始\"");
        // 无锚点评论不出现任何锚点/版本身份字段
        assertThat(legacyJson).doesNotContain("spaceId", "anchor", "versionId", "revision", "contentHash", "anchorState");
    }

    private static List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }
}
