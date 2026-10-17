package online.yudream.base.application.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.ai.cmd.AiToolTestCmd;
import online.yudream.base.application.platform.ai.dto.AiToolCatalogRowDTO;
import online.yudream.base.application.platform.ai.dto.AiToolTestResultDTO;
import online.yudream.base.application.platform.agent.workflow.support.AgentToolExecutor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.agent.service.AgentPluginToolGateway;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端工具测试：按当前登录用户的权限快照真实调用一次工具，
 * 用于确认系统 / 插件 / MCP / Python 工具是否真的可用（写入类工具的真实副作用由前端确认承担）。
 * <p>
 * 与运行期的差别只有一处：不要求工具被某个 Agent 应用授权——测试入口自身的
 * {@code platform:agent:tool:test} 权限就是授权来源；模块开关、工具启用状态、工具权限码、
 * Python 运行器可用性与沙箱守卫照旧全部复核。
 */
@Slf4j
@Service
public class AiToolTestAppService {

    private static final String INTEGRATION_CAPABILITY = "integration";

    private final AiToolModuleAppService toolModules;
    private final AgentToolRepo toolRepo;
    private final AgentPermissionGateway permissionGateway;
    private final CapabilityAppService capabilityAppService;
    private final AgentToolExecutor toolExecutor;

    public AiToolTestAppService(
            AiToolModuleAppService toolModules,
            AgentToolRepo toolRepo,
            AgentPermissionGateway permissionGateway,
            CapabilityAppService capabilityAppService,
            ObjectProvider<RuntimeExecutor> runtimeExecutor,
            ObjectProvider<AgentPluginToolGateway> pluginToolGateways,
            ObjectProvider<AgentToolExecutionGuard> executionGuard
    ) {
        this.toolModules = toolModules;
        this.toolRepo = toolRepo;
        this.permissionGateway = permissionGateway;
        this.capabilityAppService = capabilityAppService;
        // 复用工作流工具执行器的解析/校验语义；系统工具列表传空，宿主工具由本服务按目录行解析。
        this.toolExecutor = new AgentToolExecutor(
                new ObjectMapper(),
                runtimeExecutor.getIfAvailable(),
                toolRepo,
                List.of(),
                permissionGateway,
                pluginToolGateways,
                executionGuard.getIfAvailable(),
                null,
                null
        );
    }

    public AiToolTestResultDTO test(AiToolTestCmd cmd) {
        if (cmd == null || cmd.getCode() == null || cmd.getCode().isBlank()) {
            throw new BizException("工具编码不能为空");
        }
        String code = cmd.getCode().trim();
        AiToolCatalogRowDTO row = toolModules.row(code);
        if (row == null) {
            throw new BizException("工具不存在：" + code);
        }
        if (!row.testable()) {
            throw new BizException("该工具不支持在此测试：" + code);
        }
        Map<String, Object> arguments = cmd.getArguments() == null ? Map.of() : new LinkedHashMap<>(cmd.getArguments());
        AgentToolExecutor.PermissionSnapshot snapshot = new AgentToolExecutor.PermissionSnapshot(
                cmd.getPermissionCodes() == null ? List.of() : List.copyOf(cmd.getPermissionCodes()), true);
        long started = System.currentTimeMillis();
        try {
            AiAgentToolResult result = invoke(row, arguments, snapshot);
            return AiToolTestResultDTO.success(code, row.source(),
                    result.action() == null ? "completed" : result.action(),
                    result.message() == null || result.message().isBlank() ? "工具调用完成。" : result.message(),
                    System.currentTimeMillis() - started,
                    result.payload() == null ? Map.of() : result.payload());
        } catch (BizException exception) {
            return AiToolTestResultDTO.failure(code, row.source(), exception.getMessage(),
                    System.currentTimeMillis() - started);
        } catch (RuntimeException exception) {
            log.debug("AI tool test failed: code={}", code, exception);
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            return AiToolTestResultDTO.failure(code, row.source(), "工具调用异常：" + message,
                    System.currentTimeMillis() - started);
        }
    }

    private AiAgentToolResult invoke(AiToolCatalogRowDTO row, Map<String, Object> arguments,
                                     AgentToolExecutor.PermissionSnapshot snapshot) {
        return switch (row.source()) {
            case "python" -> invokePython(row, arguments, snapshot);
            case "mcp" -> invokeHostTool(row, toolModules.resolveMcpTool(row.code()), arguments, snapshot, "MCP 服务未启用或工具已下线：");
            default -> invokeHostTool(row, toolModules.resolveTool(row.code()), arguments, snapshot, "工具当前未注册（插件未加载或服务未启用）：");
        };
    }

    private AiAgentToolResult invokeHostTool(AiToolCatalogRowDTO row, AiAgentTool tool, Map<String, Object> arguments,
                                             AgentToolExecutor.PermissionSnapshot snapshot, String missingMessage) {
        if (tool == null) {
            throw new BizException(missingMessage + row.code());
        }
        toolModules.ensureToolAvailable(tool);
        ensurePermission(tool.descriptor().permissionCode(), tool.descriptor().title(), snapshot);
        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(List.of(tool), null)) {
            return tool.execute(new AiAgentToolCall(tool.descriptor().name(), arguments));
        }
    }

    private AiAgentToolResult invokePython(AiToolCatalogRowDTO row, Map<String, Object> arguments,
                                           AgentToolExecutor.PermissionSnapshot snapshot) {
        var tool = toolRepo.findByCode(row.code())
                .orElseThrow(() -> new BizException("Python 工具不存在：" + row.code()));
        if (!Boolean.TRUE.equals(tool.getEnabled())) {
            throw new BizException("Python 工具已停用：" + row.code());
        }
        capabilityAppService.ensureEnabled(INTEGRATION_CAPABILITY, "Python 工具运行");
        return toolExecutor.executePythonTool(row.code(), arguments, snapshot);
    }

    private void ensurePermission(String permissionCode, String toolName, AgentToolExecutor.PermissionSnapshot snapshot) {
        if (!permissionGateway.hasPermission(permissionCode, snapshot.permissionCodes(), snapshot.permissionContextExplicit())) {
            throw new BizException("当前用户无权调用工具："
                    + (toolName == null || toolName.isBlank() ? permissionCode : toolName));
        }
    }
}
