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
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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
                state
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

    private AgentToolRepo emptyToolRepo() {
        return new AgentToolRepo() {
            @Override public AgentTool save(AgentTool tool) { return tool; }
            @Override public Optional<AgentTool> findById(Long id) { return Optional.empty(); }
            @Override public Optional<AgentTool> findByCode(String code) { return Optional.empty(); }
            @Override public PageResult<AgentTool> page(String keyword, int page, int size) { return new PageResult<>(List.of(), 0, page, size); }
            @Override public void deleteById(Long id) { }
        };
    }
}
