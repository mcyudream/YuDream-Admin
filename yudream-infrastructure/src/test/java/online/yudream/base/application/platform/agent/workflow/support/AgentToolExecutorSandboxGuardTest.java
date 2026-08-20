package online.yudream.base.application.platform.agent.workflow.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolType;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentToolExecutorSandboxGuardTest {

    @Test
    void guardsReadAndWriteSystemToolsImmediatelyBeforeExecution() {
        AtomicInteger allowed = new AtomicInteger();
        AgentToolExecutor executor = new AgentToolExecutor(new ObjectMapper(), (script, input) -> null,
                emptyRepo(), List.of(tool("read", AgentToolRisk.READ), tool("write", AgentToolRisk.WRITE)),
                permission -> true, null, (name, risk, permission) -> {
                    if (risk != AgentToolRisk.READ) throw new BizException("blocked");
                    allowed.incrementAndGet();
                });
        AgentApplication application = AgentApplication.builder().name("app").code("app")
                .toolCodes(List.of("read", "write")).build();
        AgentRunCmd command = new AgentRunCmd();
        command.setPermissionCodes(List.of("tool:use"));
        command.setPermissionContextExplicit(true);

        assertEquals("read", executor.execute("read", Map.of(), application, command).action());
        assertThrows(BizException.class, () -> executor.execute("write", Map.of(), application, command));
        assertEquals(1, allowed.get());
    }

    @Test
    void rejectsPythonToolBeforeRuntimeExecution() {
        AtomicInteger runtimeCalls = new AtomicInteger();
        AgentTool python = AgentTool.builder().name("python").code("python_tool").type(AgentToolType.PYTHON)
                .inputSchemaJson("{\"type\":\"object\"}").pythonCode("def run(params): return params")
                .permissionCode("tool:use").enabled(true).timeoutMillis(1000).build();
        AgentToolExecutor executor = new AgentToolExecutor(new ObjectMapper(), (script, input) -> {
            runtimeCalls.incrementAndGet();
            return null;
        }, repo(python), List.of(), permission -> true, null,
                (name, risk, permission) -> { throw new BizException("blocked"); });
        AgentApplication application = AgentApplication.builder().name("app").code("app")
                .toolCodes(List.of("python_tool")).build();
        AgentRunCmd command = new AgentRunCmd();
        command.setPermissionCodes(List.of("tool:use"));
        command.setPermissionContextExplicit(true);

        assertThrows(BizException.class, () -> executor.execute("python_tool", Map.of(), application, command));
        assertEquals(0, runtimeCalls.get());
    }

    private AgentToolRepo repo(AgentTool value) {
        return new AgentToolRepo() {
            @Override public AgentTool save(AgentTool tool) { return tool; }
            @Override public Optional<AgentTool> findById(Long id) { return Optional.empty(); }
            @Override public Optional<AgentTool> findByCode(String code) {
                return value.getCode().equals(code) ? Optional.of(value) : Optional.empty();
            }
            @Override public PageResult<AgentTool> page(String keyword, int page, int size) {
                return new PageResult<>(List.of(value), 1, page, size);
            }
            @Override public void deleteById(Long id) { }
        };
    }

    private AiAgentTool tool(String name, AgentToolRisk risk) {
        return new AiAgentTool() {
            @Override public AiAgentToolDescriptor descriptor() {
                return new AiAgentToolDescriptor(name, name, name, "tool:use", null, null, null, Map.of());
            }
            @Override public AgentToolRisk risk() { return risk; }
            @Override public AiAgentToolResult execute(AiAgentToolCall call) {
                return new AiAgentToolResult(name, name, "tool:use", "ok", Map.of());
            }
        };
    }

    private AgentToolRepo emptyRepo() {
        return new AgentToolRepo() {
            @Override public AgentTool save(AgentTool tool) { return tool; }
            @Override public Optional<AgentTool> findById(Long id) { return Optional.empty(); }
            @Override public Optional<AgentTool> findByCode(String code) { return Optional.empty(); }
            @Override public PageResult<AgentTool> page(String keyword, int page, int size) {
                return new PageResult<>(List.of(), 0, page, size);
            }
            @Override public void deleteById(Long id) { }
        };
    }
}
