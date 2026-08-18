package online.yudream.base.interfaces.platform.wiki;

import online.yudream.base.application.platform.wiki.dto.WikiChatActivityDTO;
import online.yudream.base.application.platform.wiki.dto.WikiChatResultDTO;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiAguiWebAssembler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WikiAguiWebAssemblerTest {

    @Test
    @SuppressWarnings("unchecked")
    void runFinishedCarriesCitationExcerptForPassagePositioning() {
        var citation = WikiChatResultDTO.Citation.builder()
                .title("虚拟线程的并发资源治理")
                .path("virtual-thread-governance")
                .nodeId("123456789012345678")
                .excerpt("数据库连接、文件描述符、网络连接和下游服务容量仍然可能被耗尽。")
                .build();
        var result = WikiChatResultDTO.builder()
                .answer("虚拟线程仍需控制外部资源并发。")
                .citations(List.of(citation))
                .build();

        var event = WikiAguiWebAssembler.runFinished("run-1", result);

        assertThat(event.getType()).isEqualTo("RUN_FINISHED");
        assertThat(event.getResult()).isInstanceOf(Map.class);
        Map<String, Object> payload = (Map<String, Object>) event.getResult();
        List<Map<String, Object>> citations = (List<Map<String, Object>>) payload.get("citations");
        assertThat(citations).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("nodeId", "123456789012345678");
            assertThat(item).containsEntry("excerpt", "数据库连接、文件描述符、网络连接和下游服务容量仍然可能被耗尽。");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrievalSnapshotExposesStructuredContentAndStringNodeId() {
        var hit = WikiChatActivityDTO.Hit.builder()
                .score(0.95)
                .kind("PAGE")
                .nodeId("123456789012345678")
                .title("虚拟线程的并发资源治理")
                .path("virtual-thread-governance")
                .excerpt("数据库连接仍然可能被耗尽。")
                .build();
        var activity = WikiChatActivityDTO.builder()
                .activityType("wiki-retrieval")
                .phase("wiki-retrieval")
                .status("completed")
                .title("预检索")
                .content("预检索完成")
                .query("虚拟线程")
                .hits(List.of(hit))
                .build();

        var event = WikiAguiWebAssembler.activitySnapshot("run-1", activity);

        assertThat(event.getType()).isEqualTo("ACTIVITY_SNAPSHOT");
        assertThat(event.getActivityType()).isEqualTo("wiki-retrieval");
        assertThat(event.getMessageId()).isEqualTo("activity-run-1-wiki-retrieval");
        assertThat(event.getContent()).isInstanceOf(Map.class);
        Map<String, Object> content = (Map<String, Object>) event.getContent();
        assertThat(content).containsEntry("phase", "wiki-retrieval");
        List<Map<String, Object>> hits = (List<Map<String, Object>>) content.get("hits");
        assertThat(hits).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("nodeId", "123456789012345678");
            assertThat(item.get("nodeId")).isInstanceOf(String.class);
            assertThat(item).containsEntry("excerpt", "数据库连接仍然可能被耗尽。");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void graphSnapshotExposesStructuredNodesAndEdgesWithStringIds() {
        var node = WikiChatActivityDTO.Node.builder()
                .id("123456789012345678")
                .title("虚拟线程的并发资源治理")
                .type("CONCEPT")
                .role("page")
                .score(0.9)
                .path("virtual-thread-governance")
                .build();
        var edge = WikiChatActivityDTO.Edge.builder()
                .source("query")
                .target("123456789012345678")
                .weight(0.9)
                .signal("retrieval_match")
                .build();
        var graph = WikiChatActivityDTO.Graph.builder()
                .query("虚拟线程")
                .nodes(List.of(node))
                .edges(List.of(edge))
                .build();
        var activity = WikiChatActivityDTO.builder()
                .activityType("wiki-graph")
                .phase("wiki-graph")
                .status("completed")
                .title("知识图谱")
                .content("本轮局部图谱")
                .query("虚拟线程")
                .graph(graph)
                .build();

        var event = WikiAguiWebAssembler.activitySnapshot("run-1", activity);

        assertThat(event.getActivityType()).isEqualTo("wiki-graph");
        assertThat(event.getMessageId()).isEqualTo("activity-run-1-wiki-graph");
        Map<String, Object> content = (Map<String, Object>) event.getContent();
        Map<String, Object> graphPayload = (Map<String, Object>) content.get("graph");
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graphPayload.get("nodes");
        assertThat(nodes).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("id", "123456789012345678");
            assertThat(item.get("id")).isInstanceOf(String.class);
            assertThat(item).containsEntry("role", "page");
        });
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graphPayload.get("edges");
        assertThat(edges).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("source", "query");
            assertThat(item).containsEntry("target", "123456789012345678");
            assertThat(item).containsEntry("signal", "retrieval_match");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void toolResultContentIsStructuredMap() {
        var tool = new AiAgentToolResult("wiki.search", "search", "platform:wiki:tool:search",
                "检索到 1 条结果。", Map.of("query", "虚拟线程"));

        var event = WikiAguiWebAssembler.toolResult("run-1", "call-1", tool);

        assertThat(event.getType()).isEqualTo("TOOL_CALL_RESULT");
        assertThat(event.getContent()).isInstanceOf(Map.class);
        Map<String, Object> content = (Map<String, Object>) event.getContent();
        assertThat(content).containsEntry("toolName", "wiki.search");
        assertThat(content).containsEntry("action", "search");
        assertThat(content).doesNotContainKey("permissionCode");
    }
}
