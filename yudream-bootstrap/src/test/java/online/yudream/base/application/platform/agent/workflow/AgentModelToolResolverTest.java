package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.workflow.support.AgentModelToolResolver;
import online.yudream.base.application.platform.agent.workflow.support.AgentToolExecutor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
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

class AgentModelToolResolverTest {

    @Test
    void shouldReturnOriginalAuthorizedSystemToolInRequestedOrder() {
        AiAgentTool systemTool = systemTool();
        AgentModelToolResolver resolver = resolver(emptyToolRepo(), (script, stdin) -> failRuntime(), List.of(systemTool), permission -> true);

        List<AiAgentTool> tools = resolver.resolve(
                List.of("system.lookup", "system.lookup"),
                application("system.lookup"),
                command("system:lookup")
        );

        assertThat(tools).containsExactly(systemTool);
    }

    @Test
    void shouldAdaptEnabledPythonToolWithParsedDescriptorAndDictionaryPayload() {
        AgentTool pythonTool = pythonTool(true);
        AtomicReference<String> runtimeInput = new AtomicReference<>();
        AtomicReference<String> runtimeCode = new AtomicReference<>();
        AgentModelToolResolver resolver = resolver(
                toolRepo(pythonTool),
                (script, stdin) -> {
                    runtimeCode.set(script.getScriptContent());
                    runtimeInput.set(stdin);
                    return success("{\"score\":95,\"level\":\"high\"}");
                },
                List.of(),
                permission -> true
        );

        AiAgentTool tool = resolver.resolve(List.of("risk_score"), application("risk_score"), command("risk:score")).getFirst();
        AiAgentToolResult result = tool.execute(new AiAgentToolCall("risk_score", Map.of("score", 95)));

        assertThat(tool.descriptor().name()).isEqualTo("risk_score");
        assertThat(tool.descriptor().title()).isEqualTo("风险评分");
        assertThat(tool.descriptor().description()).isEqualTo("计算风险等级");
        assertThat(tool.descriptor().permissionCode()).isEqualTo("risk:score");
        assertThat(tool.descriptor().inputSchema()).containsEntry("type", "object");
        assertThat(tool.descriptor().inputSchema()).containsEntry("required", List.of("score"));
        assertThat(runtimeCode.get()).contains("def run(params: dict) -> dict", "run(_yudream_params)");
        assertThat(runtimeInput.get()).isEqualTo("{\"score\":95}");
        assertThat(result.payload()).containsEntry("score", 95).containsEntry("level", "high");
    }

    @Test
    void shouldRejectMissingDisabledUnauthorizedAndUngrantToolsBeforeModelExecution() {
        AgentModelToolResolver missing = resolver(emptyToolRepo(), (script, stdin) -> failRuntime(), List.of(), permission -> true);
        AgentModelToolResolver disabled = resolver(toolRepo(pythonTool(false)), (script, stdin) -> failRuntime(), List.of(), permission -> true);
        AgentModelToolResolver unauthorized = resolver(toolRepo(pythonTool(true)), (script, stdin) -> failRuntime(), List.of(), permission -> false);

        assertThatThrownBy(() -> missing.resolve(List.of("missing"), application("missing"), command("risk:score")))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> disabled.resolve(List.of("risk_score"), application("risk_score"), command("risk:score")))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> unauthorized.resolve(List.of("risk_score"), application("risk_score"), command()))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> missing.resolve(List.of("risk_score"), application(), command("risk:score")))
                .isInstanceOf(BizException.class);
    }

    private AgentModelToolResolver resolver(
            AgentToolRepo repo,
            online.yudream.base.domain.platform.integration.service.RuntimeExecutor runtime,
            List<AiAgentTool> systemTools,
            online.yudream.base.domain.platform.agent.service.AgentPermissionGateway permissions
    ) {
        return new AgentModelToolResolver(new AgentToolExecutor(
                new ObjectMapper(), runtime, repo, systemTools, permissions
        ));
    }

    private AgentApplication application(String... toolCodes) {
        return AgentApplication.builder().name("测试应用").code("test-app").toolCodes(List.of(toolCodes)).build();
    }

    private AgentRunCmd command(String... permissionCodes) {
        AgentRunCmd command = new AgentRunCmd();
        command.setPermissionCodes(List.of(permissionCodes));
        command.setPermissionContextExplicit(true);
        return command;
    }

    private AiAgentTool systemTool() {
        return new AiAgentTool() {
            @Override
            public AiAgentToolDescriptor descriptor() {
                return new AiAgentToolDescriptor(
                        "system.lookup", "系统查询", "查询系统记录", "system:lookup", "系统查询", "系统", "查询系统记录", Map.of()
                );
            }

            @Override
            public AiAgentToolResult execute(AiAgentToolCall call) {
                return new AiAgentToolResult("system.lookup", "lookup", "system:lookup", "完成", Map.of());
            }
        };
    }

    private AgentTool pythonTool(boolean enabled) {
        return AgentTool.builder()
                .name("风险评分")
                .code("risk_score")
                .description("计算风险等级")
                .type(AgentToolType.PYTHON)
                .inputSchemaJson("{\"type\":\"object\",\"required\":[\"score\"]}")
                .outputExampleJson("{\"score\":95,\"level\":\"high\"}")
                .pythonCode("def run(params: dict) -> dict:\n    return {\"score\": params[\"score\"]}")
                .timeoutMillis(10_000)
                .permissionCode("risk:score")
                .enabled(enabled)
                .build();
    }

    private AgentToolRepo emptyToolRepo() {
        return toolRepo(null);
    }

    private AgentToolRepo toolRepo(AgentTool tool) {
        return new AgentToolRepo() {
            @Override public AgentTool save(AgentTool value) { return value; }
            @Override public Optional<AgentTool> findById(Long id) { return Optional.empty(); }
            @Override public Optional<AgentTool> findByCode(String code) {
                return tool != null && tool.getCode().equals(code) ? Optional.of(tool) : Optional.empty();
            }
            @Override public PageResult<AgentTool> page(String keyword, int page, int size) {
                return new PageResult<>(tool == null ? List.of() : List.of(tool), tool == null ? 0 : 1, page, size);
            }
            @Override public void deleteById(Long id) { }
        };
    }

    private RuntimeExecutionResult success(String output) {
        return new RuntimeExecutionResult(output, "", 0, 1, ExecutionStatus.SUCCESS, null);
    }

    private RuntimeExecutionResult failRuntime() {
        throw new AssertionError("不应执行 Python Runtime");
    }
}
