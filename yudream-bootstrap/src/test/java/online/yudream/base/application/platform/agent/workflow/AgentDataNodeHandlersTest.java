package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.workflow.handler.AgentCitationNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentCodeNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentDocumentNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentEndNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentStartNodeHandler;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.valobj.RuntimeExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDataNodeHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentWorkflowValueResolver values = new AgentWorkflowValueResolver(objectMapper);

    @Test
    void shouldExecutePythonCodeWithJsonContextAndParseJsonOutput() {
        AtomicReference<String> stdin = new AtomicReference<>();
        AgentCodeNodeHandler code = new AgentCodeNodeHandler(values, objectMapper, (script, input) -> {
            stdin.set(input);
            assertThat(script.getScriptContent()).contains("print");
            return new RuntimeExecutionResult("{\"value\":42}\n", "", 0, 4, ExecutionStatus.SUCCESS, null);
        });
        AgentWorkflowExecution execution = executor(code).execute("""
                {"nodes":[
                  {"id":"start","data":{"kind":"start","outputVariable":"query"}},
                  {"id":"code","data":{"kind":"code","code":"print(42)","inputVariable":"query","outputVariable":"calculated"}},
                  {"id":"end","data":{"kind":"end","inputVariable":"calculated"}}
                ],"edges":[
                  {"source":"start","target":"code"},{"source":"code","target":"end"}
                ]}
                """, "question");

        assertThat(stdin.get()).contains("\"input\":\"question\"").contains("\"query\":\"question\"");
        assertThat(execution.context().nodeOutput("end")).isEqualTo(Map.of("value", 42));
    }

    @Test
    void shouldExtractDocumentFromConfiguredInput() {
        AgentDocumentNodeHandler document = new AgentDocumentNodeHandler(values, source -> "正文:" + source.content());
        AgentWorkflowExecution execution = executor(document).execute("""
                {"nodes":[
                  {"id":"start","data":{"kind":"start"}},
                  {"id":"document","data":{"kind":"document","documentInput":"input","outputVariable":"document"}},
                  {"id":"end","data":{"kind":"end","inputVariable":"document"}}
                ],"edges":[
                  {"source":"start","target":"document"},{"source":"document","target":"end"}
                ]}
                """, "data:text/plain;base64,5rWL6K+V");

        assertThat(execution.context().nodeOutput("end")).isEqualTo("正文:data:text/plain;base64,5rWL6K+V");
    }

    @Test
    void shouldFormatSearchHitsAsMarkdownCitations() {
        AgentCitationNodeHandler citation = new AgentCitationNodeHandler(values, objectMapper);
        List<Map<String, Object>> hits = List.of(Map.of(
                "title", "部署手册",
                "path", "/ops/deploy",
                "content", "先配置环境变量",
                "sourceUrl", "/wiki/docs/ops/deploy",
                "score", 0.92
        ));
        AgentWorkflowExecution execution = executor(citation).execute("""
                {"nodes":[
                  {"id":"start","data":{"kind":"start"}},
                  {"id":"citation","data":{"kind":"citation","citationSource":"input","citationFormat":"markdown","outputVariable":"citations"}},
                  {"id":"end","data":{"kind":"end","inputVariable":"citations"}}
                ],"edges":[
                  {"source":"start","target":"citation"},{"source":"citation","target":"end"}
                ]}
                """, hits);

        assertThat(execution.context().nodeOutput("end").toString())
                .contains("[1]", "部署手册", "/wiki/docs/ops/deploy", "先配置环境变量");
    }

    private AgentWorkflowExecutor executor(AgentWorkflowNodeHandler handler) {
        return new AgentWorkflowExecutor(
                new AgentWorkflowGraphParser(objectMapper),
                List.of(new AgentStartNodeHandler(values), handler, new AgentEndNodeHandler(values))
        );
    }
}
