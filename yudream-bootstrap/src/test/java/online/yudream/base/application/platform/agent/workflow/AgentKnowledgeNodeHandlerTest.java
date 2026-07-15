package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.workflow.handler.AgentEndNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentKnowledgeNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentStartNodeHandler;
import online.yudream.base.application.platform.agent.workflow.support.AgentKnowledgeOperations;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.domain.platform.wiki.valobj.WikiSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentKnowledgeNodeHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentWorkflowValueResolver values = new AgentWorkflowValueResolver(objectMapper);
    private final FakeKnowledgeOperations operations = new FakeKnowledgeOperations();

    @Test
    void shouldExecuteSearchAndVectorWithKnowledgeSpaceSettings() {
        Object search = run("search", """
                "knowledgeSpaceSlug":"docs","topK":6,"pathPrefix":"/guide","graphExpansion":true
                """, "安装");
        assertThat(search).isEqualTo(operations.hit("search:安装"));
        assertThat(operations.lastCall).isEqualTo("search:docs:安装:6:/guide:true");

        Object vector = run("vector", """
                "knowledgeSpaceSlug":"docs","topK":4,"pathPrefix":"/api","graphExpansion":false
                """, "鉴权");
        assertThat(vector).isEqualTo(operations.hit("vector:鉴权"));
        assertThat(operations.lastCall).isEqualTo("vector:docs:鉴权:4:/api:false");
    }

    @Test
    void shouldExecuteEmbeddingWithSelectedProviderAndModel() {
        Object output = run("embedding", """
                "providerCode":"openai","modelCode":"embed-3"
                """, "需要向量化的文本");

        assertThat(output).isEqualTo(List.of(0.1f, 0.2f));
        assertThat(operations.lastCall).isEqualTo("embedding::openai:embed-3:需要向量化的文本");
    }

    @Test
    void shouldExecuteRerankWithSelectedProviderAndModel() {
        List<WikiSearchHit> candidates = List.of(
                new WikiSearchHit(0.5, 1L, "A", "/a", "first"),
                new WikiSearchHit(0.4, 2L, "B", "/b", "second")
        );
        Object output = run("rerank", """
                "providerCode":"cohere","modelCode":"rerank-v3"
                """, candidates);

        assertThat(output).isEqualTo(candidates.reversed());
        assertThat(operations.lastCall).startsWith("rerank::cohere:rerank-v3:");
    }

    private Object run(String kind, String config, Object input) {
        AgentWorkflowExecutor executor = new AgentWorkflowExecutor(
                new AgentWorkflowGraphParser(objectMapper),
                List.of(
                        new AgentStartNodeHandler(values),
                        new AgentKnowledgeNodeHandler(kind, values, objectMapper, operations),
                        new AgentEndNodeHandler(values)
                )
        );
        AgentWorkflowExecution execution = executor.execute("""
                {"nodes":[
                  {"id":"start","data":{"kind":"start","outputVariable":"query"}},
                  {"id":"knowledge","data":{"kind":"%s","outputVariable":"result",%s}},
                  {"id":"end","data":{"kind":"end","inputVariable":"result"}}
                ],"edges":[
                  {"source":"start","target":"knowledge"},{"source":"knowledge","target":"end"}
                ]}
                """.formatted(kind, config), input);
        return execution.context().nodeOutput("end");
    }

    private static final class FakeKnowledgeOperations implements AgentKnowledgeOperations {
        private String lastCall;

        @Override
        public List<WikiSearchHit> search(String spaceSlug, String query, int topK, String pathPrefix, boolean graphExpansion) {
            lastCall = "search:%s:%s:%d:%s:%s".formatted(spaceSlug, query, topK, pathPrefix, graphExpansion);
            return hit("search:" + query);
        }

        @Override
        public List<WikiSearchHit> vectorSearch(String spaceSlug, String query, int topK, String pathPrefix, boolean graphExpansion) {
            lastCall = "vector:%s:%s:%d:%s:%s".formatted(spaceSlug, query, topK, pathPrefix, graphExpansion);
            return hit("vector:" + query);
        }

        @Override
        public List<WikiSearchHit> rerank(String spaceSlug, String providerCode, String modelCode, String query, List<WikiSearchHit> candidates) {
            lastCall = "rerank:%s:%s:%s:%s:%d".formatted(spaceSlug, providerCode, modelCode, query, candidates.size());
            return candidates.reversed();
        }

        @Override
        public List<List<Float>> embedding(String spaceSlug, String providerCode, String modelCode, List<String> texts) {
            lastCall = "embedding:%s:%s:%s:%s".formatted(spaceSlug, providerCode, modelCode, texts.getFirst());
            return List.of(List.of(0.1f, 0.2f));
        }

        private List<WikiSearchHit> hit(String content) {
            return List.of(new WikiSearchHit(0.9, 1L, "文档", "/docs", content));
        }
    }
}
