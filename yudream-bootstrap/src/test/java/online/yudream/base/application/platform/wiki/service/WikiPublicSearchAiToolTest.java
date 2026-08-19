package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikiPublicSearchAiToolTest {

    @Test
    @SuppressWarnings("unchecked")
    void executeForcesPublicSearchAndIgnoresSourceGrounded() {
        WikiSearchAppService search = mock(WikiSearchAppService.class);
        when(search.searchForPublicSite(anyString(), anyString(), anyInt(), anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of(WikiSearchHitDTO.builder()
                        .score(0.9)
                        .kind("PAGE")
                        .nodeId("1")
                        .title("已发布页")
                        .path("published")
                        .content("内容")
                        .build()));
        WikiPublicSearchAiTool tool = new WikiPublicSearchAiTool(search, mock(WikiPublicAppService.class));

        var result = tool.execute(new AiAgentToolCall("wiki.search", Map.of(
                "spaceSlug", "demo",
                "query", "虚拟线程",
                "sourceGrounded", true)));

        verify(search).searchForPublicSite(eq("demo"), eq("虚拟线程"), anyInt(), anyString(), anyBoolean(), eq(false));
        assertThat(result.toolName()).isEqualTo("wiki.search");
        Map<String, Object> payload = result.payload();
        assertThat(payload).containsEntry("count", 1);
        assertThat(payload.get("hits")).isInstanceOf(List.class);
        List<Map<String, Object>> hits = (List<Map<String, Object>>) payload.get("hits");
        assertThat(hits).singleElement().satisfies(item ->
                assertThat(item).containsEntry("nodeId", "1"));
    }

    @Test
    void blankSpaceSlugSearchesAllPublicSpaces() {
        WikiSearchAppService search = mock(WikiSearchAppService.class);
        WikiPublicAppService publicSearch = mock(WikiPublicAppService.class);
        when(publicSearch.searchAll(eq("登录报错"), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of(WikiSearchHitDTO.builder()
                        .score(0.8)
                        .kind("PAGE")
                        .title("登录排障")
                        .spaceSlug("tutorial")
                        .spaceName("教程")
                        .content("步骤")
                        .images(List.of(WikiSearchHitDTO.Image.builder()
                                .url("/api/files/123/content")
                                .alt("配置截图")
                                .generatedCaption("设置页中的保存按钮")
                                .caption("配置截图")
                                .build()))
                        .build()));
        WikiPublicSearchAiTool tool = new WikiPublicSearchAiTool(search, publicSearch);

        var result = tool.execute(new AiAgentToolCall("wiki.search", Map.of("query", "登录报错")));

        verify(publicSearch).searchAll(eq("登录报错"), org.mockito.ArgumentMatchers.isNull());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) result.payload().get("hits");
        assertThat(result.payload()).containsEntry("count", 1);
        assertThat(hits).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("spaceSlug", "tutorial").containsEntry("spaceName", "教程");
            assertThat(item.get("images")).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> images = (List<Map<String, Object>>) item.get("images");
            assertThat(images).singleElement().satisfies(image -> assertThat(image)
                    .containsEntry("index", 1)
                    .containsEntry("alt", "配置截图")
                    .containsEntry("generatedCaption", "设置页中的保存按钮")
                    .containsEntry("caption", "配置截图"));
        });
    }

    @Test
    void publicDescriptorSharesWikiSearchToolName() {
        WikiSearchAppService search = mock(WikiSearchAppService.class);
        WikiPublicSearchAiTool tool = new WikiPublicSearchAiTool(search, mock(WikiPublicAppService.class));

        assertThat(tool.descriptor().name()).isEqualTo("wiki.search");
        assertThat(tool.descriptor().inputSchema()).containsKeys("spaceSlug", "query", "sourceGrounded");
    }
}
