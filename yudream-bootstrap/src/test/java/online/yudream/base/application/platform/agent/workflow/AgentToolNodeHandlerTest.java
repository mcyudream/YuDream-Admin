package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.workflow.handler.AgentEndNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentStartNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentToolNodeHandler;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowRunState;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolType;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.valobj.RuntimeExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentToolNodeHandlerTest {

    @Test
    void shouldDirectlyExecuteAuthorizedSystemToolWithMappedArguments() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentWorkflowValueResolver values = new AgentWorkflowValueResolver(objectMapper);
        AgentApplication application = AgentApplication.builder()
                .name("查询应用")
                .code("query")
                .toolCodes(List.of("system.lookup"))
                .build();
        AgentRunCmd cmd = new AgentRunCmd();
        cmd.setInput("{\"keyword\":\"订单\"}");
        cmd.setPermissionCodes(List.of("order:view"));
        cmd.setPermissionContextExplicit(true);
        AtomicReference<AiAgentToolCall> call = new AtomicReference<>();
        AiAgentTool systemTool = new AiAgentTool() {
            @Override
            public AiAgentToolDescriptor descriptor() {
                return new AiAgentToolDescriptor(
                        "system.lookup", "查询", "查询订单", "order:view", "查看订单", "订单", "查询订单", Map.of()
                );
            }

            @Override
            public AiAgentToolResult execute(AiAgentToolCall value) {
                call.set(value);
                return new AiAgentToolResult("system.lookup", "lookup", "order:view", "完成", Map.of("count", 2));
            }
        };
        AgentWorkflowRunState state = new AgentWorkflowRunState(
                application, cmd, Map.of(), java.util.Set.of("system.lookup"), null, ignored -> { }
        );
        AgentToolNodeHandler handler = new AgentToolNodeHandler(
                values,
                objectMapper,
                (script, stdin) -> { throw new AssertionError("不应执行 Python"); },
                emptyToolRepo(),
                List.of(systemTool),
                application,
                state,
                permission -> true
        );
        AgentWorkflowExecutor executor = new AgentWorkflowExecutor(
                new AgentWorkflowGraphParser(objectMapper),
                List.of(new AgentStartNodeHandler(values), handler, new AgentEndNodeHandler(values))
        );

        AgentWorkflowExecution execution = executor.execute("""
                {"nodes":[
                  {"id":"start","data":{"kind":"start","outputVariable":"arguments"}},
                  {"id":"tool","data":{"kind":"tool","toolCode":"system.lookup","inputVariable":"arguments","outputVariable":"result"}},
                  {"id":"end","data":{"kind":"end","inputVariable":"result"}}
                ],"edges":[
                  {"source":"start","target":"tool"},{"source":"tool","target":"end"}
                ]}
                """, cmd.getInput());

        assertThat(call.get().arguments()).containsEntry("keyword", "订单");
        assertThat(execution.context().nodeOutput("end")).isEqualTo(Map.of("count", 2));
        assertThat(state.toolResults()).singleElement().satisfies(result -> assertThat(result.toolName()).isEqualTo("system.lookup"));
    }

    @Test
    void shouldInvokePythonRunFunctionAndExposeReturnedDictionaryAsToolPayload() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentWorkflowValueResolver values = new AgentWorkflowValueResolver(objectMapper);
        AgentTool customTool = AgentTool.builder()
                .name("风险评分")
                .code("risk_score")
                .description("计算风险分")
                .type(AgentToolType.PYTHON)
                .inputSchemaJson("{\"type\":\"object\"}")
                .outputExampleJson("{\"score\":95,\"level\":\"high\"}")
                .pythonCode("def run(params: dict) -> dict:\n    return {\"score\": params[\"score\"]}")
                .timeoutMillis(10_000)
                .enabled(true)
                .build();
        AgentApplication application = AgentApplication.builder()
                .name("评分应用")
                .code("score")
                .toolCodes(List.of("risk_score"))
                .build();
        AgentRunCmd cmd = new AgentRunCmd();
        cmd.setInput("{\"score\":95}");
        AtomicReference<String> executedCode = new AtomicReference<>();
        AtomicReference<String> executedInput = new AtomicReference<>();
        AgentWorkflowRunState state = new AgentWorkflowRunState(
                application, cmd, Map.of(), java.util.Set.of(), null, ignored -> { }
        );
        AgentToolNodeHandler handler = new AgentToolNodeHandler(
                values,
                objectMapper,
                (script, input) -> {
                    executedCode.set(script.getScriptContent());
                    executedInput.set(input);
                    return new RuntimeExecutionResult(
                            "{\"score\":95,\"level\":\"high\"}", "", 0, 5,
                            ExecutionStatus.SUCCESS, null
                    );
                },
                toolRepo(customTool),
                List.of(),
                application,
                state,
                permission -> true
        );
        AgentWorkflowExecutor executor = new AgentWorkflowExecutor(
                new AgentWorkflowGraphParser(objectMapper),
                List.of(new AgentStartNodeHandler(values), handler, new AgentEndNodeHandler(values))
        );

        AgentWorkflowExecution execution = executor.execute("""
                {"nodes":[
                  {"id":"start","data":{"kind":"start","outputVariable":"arguments"}},
                  {"id":"tool","data":{"kind":"tool","toolCode":"risk_score","inputVariable":"arguments","outputVariable":"result"}},
                  {"id":"end","data":{"kind":"end","inputVariable":"result"}}
                ],"edges":[
                  {"source":"start","target":"tool"},{"source":"tool","target":"end"}
                ]}
                """, cmd.getInput());

        assertThat(executedCode.get()).contains("def run(params: dict) -> dict", "run(_yudream_params)");
        assertThat(objectMapper.readTree(executedInput.get()).get("score").asInt()).isEqualTo(95);
        assertThat(execution.context().nodeOutput("end")).isEqualTo(Map.of("score", 95, "level", "high"));
        assertThat(state.toolResults()).singleElement().satisfies(result -> {
            assertThat(result.payload()).containsEntry("score", 95).containsEntry("level", "high");
            assertThat(result.payload()).doesNotContainKey("stdout");
        });
    }

    @Test
    void shouldRejectPythonRunResultThatIsNotADictionary() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentWorkflowValueResolver values = new AgentWorkflowValueResolver(objectMapper);
        AgentTool customTool = AgentTool.builder()
                .name("风险评分")
                .code("risk_score")
                .type(AgentToolType.PYTHON)
                .inputSchemaJson("{\"type\":\"object\"}")
                .outputExampleJson("{\"score\":95}")
                .pythonCode("def run(params: dict) -> dict:\n    return [params]")
                .timeoutMillis(10_000)
                .enabled(true)
                .build();
        AgentApplication application = AgentApplication.builder()
                .name("评分应用")
                .code("score")
                .toolCodes(List.of("risk_score"))
                .build();
        AgentRunCmd cmd = new AgentRunCmd();
        cmd.setInput("{\"score\":95}");
        AgentWorkflowRunState state = new AgentWorkflowRunState(
                application, cmd, Map.of(), java.util.Set.of(), null, ignored -> { }
        );
        AgentToolNodeHandler handler = new AgentToolNodeHandler(
                values,
                objectMapper,
                (script, input) -> new RuntimeExecutionResult(
                        "[95]", "", 0, 5, ExecutionStatus.SUCCESS, null
                ),
                toolRepo(customTool),
                List.of(),
                application,
                state,
                permission -> true
        );

        assertThatThrownBy(() -> handler.execute(
                new AgentWorkflowNode(
                        "tool",
                        "agent",
                        "风险评分",
                        objectMapper.readTree("""
                                {"kind":"tool","toolCode":"risk_score","inputVariable":"input","outputVariable":"result"}
                                """)
                ),
                new AgentWorkflowContext(Map.of("score", 95))
        ))
                .isInstanceOf(online.yudream.base.domain.common.exception.BizException.class)
                .hasMessage("Python tool run() must return a serializable dict");
    }

    private AgentToolRepo emptyToolRepo() {
        return new AgentToolRepo() {
            @Override public AgentTool save(AgentTool tool) { return tool; }
            @Override public Optional<AgentTool> findById(Long id) { return Optional.empty(); }
            @Override public Optional<AgentTool> findByCode(String code) { return Optional.empty(); }
            @Override public PageResult<AgentTool> page(String keyword, int page, int size) { return new PageResult<>(List.of(), 0, page, size); }
            @Override public void deleteById(Long id) { }
        };
    }

    private AgentToolRepo toolRepo(AgentTool tool) {
        return new AgentToolRepo() {
            @Override public AgentTool save(AgentTool value) { return value; }
            @Override public Optional<AgentTool> findById(Long id) { return Optional.empty(); }
            @Override public Optional<AgentTool> findByCode(String code) { return tool.getCode().equals(code) ? Optional.of(tool) : Optional.empty(); }
            @Override public PageResult<AgentTool> page(String keyword, int page, int size) { return new PageResult<>(List.of(tool), 1, page, size); }
            @Override public void deleteById(Long id) { }
        };
    }
}
