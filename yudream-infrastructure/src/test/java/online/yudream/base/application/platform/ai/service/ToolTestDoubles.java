package online.yudream.base.application.platform.ai.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import online.yudream.base.domain.platform.agent.service.AgentPluginToolGateway;
import online.yudream.base.domain.platform.ai.aggregate.AiToolSettings;
import online.yudream.base.domain.platform.ai.repo.AiToolSettingsRepo;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import online.yudream.base.domain.platform.integration.valobj.RuntimeExecutionResult;
import online.yudream.base.domain.platform.mcp.aggregate.McpServer;
import online.yudream.base.domain.platform.mcp.enumerate.McpTransport;
import online.yudream.base.domain.platform.mcp.repo.McpServerRepo;
import online.yudream.base.domain.platform.mcp.service.McpAgentToolProvider;
import online.yudream.base.domain.platform.mcp.valobj.McpToolDefinition;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 工具模块与工具测试的测试替身（基础设施测试树不使用 Mockito）。 */
final class ToolTestDoubles {

    private ToolTestDoubles() {
    }

    static final class FakeObjectProvider<T> implements ObjectProvider<T> {
        private final List<T> values;

        FakeObjectProvider(List<T> values) {
            this.values = values == null ? List.of() : List.copyOf(values);
        }

        @Override
        public T getObject() throws BeansException {
            return values.isEmpty() ? null : values.get(0);
        }

        @Override
        public T getObject(Object... args) throws BeansException {
            return getObject();
        }

        @Override
        public T getIfAvailable() throws BeansException {
            return getObject();
        }

        @Override
        public T getIfUnique() throws BeansException {
            return values.size() == 1 ? values.get(0) : null;
        }

        @Override
        public Iterator<T> iterator() {
            return values.iterator();
        }
    }

    /** 能力服务替身：默认放行，可指定某个能力拒绝。 */
    static final class StubCapabilityAppService extends CapabilityAppService {
        final List<String> ensured = new ArrayList<>();
        String deny;

        StubCapabilityAppService() {
            super(null, List.of());
        }

        @Override
        public boolean enabled(String code) {
            return !code.equals(deny);
        }

        @Override
        public void ensureEnabled(String code, String name) {
            if (code.equals(deny)) {
                throw new BizException(name + "能力未启用，请先在平台能力中启用");
            }
            ensured.add(code);
        }
    }

    static final class MemoryToolSettingsRepo implements AiToolSettingsRepo {
        private AiToolSettings value = AiToolSettings.defaults();

        @Override
        public AiToolSettings load() {
            return value;
        }

        @Override
        public AiToolSettings save(AiToolSettings settings) {
            this.value = settings;
            return value;
        }
    }

    static final class MemoryMcpServerRepo implements McpServerRepo {
        final Map<String, McpServer> servers = new LinkedHashMap<>();
        private long nextId = 1L;

        @Override
        public List<McpServer> findAll() {
            return List.copyOf(servers.values());
        }

        @Override
        public Optional<McpServer> findById(Long id) {
            return servers.values().stream().filter(server -> id != null && id.equals(server.getId())).findFirst();
        }

        @Override
        public Optional<McpServer> findByCode(String code) {
            return Optional.ofNullable(servers.get(code));
        }

        @Override
        public McpServer save(McpServer server) {
            if (server.getId() == null) {
                server.setId(nextId++);
            }
            servers.put(server.getCode(), server);
            return server;
        }

        @Override
        public void deleteById(Long id) {
            servers.values().removeIf(server -> id != null && id.equals(server.getId()));
        }
    }

    static final class StubAgentToolRepo implements AgentToolRepo {
        final List<AgentTool> tools = new ArrayList<>();

        @Override
        public AgentTool save(AgentTool tool) {
            tools.removeIf(existing -> existing.getCode().equals(tool.getCode()));
            tools.add(tool);
            return tool;
        }

        @Override
        public Optional<AgentTool> findById(Long id) {
            return tools.stream().filter(tool -> id != null && id.equals(tool.getId())).findFirst();
        }

        @Override
        public Optional<AgentTool> findByCode(String code) {
            return tools.stream().filter(tool -> tool.getCode().equals(code)).findFirst();
        }

        @Override
        public PageResult<AgentTool> page(String keyword, int page, int size) {
            List<AgentTool> matched = tools.stream()
                    .filter(tool -> keyword == null || keyword.isBlank()
                            || tool.getName().toLowerCase().contains(keyword.toLowerCase())
                            || tool.getCode().toLowerCase().contains(keyword.toLowerCase()))
                    .toList();
            return new PageResult<>(matched, matched.size(), page, size);
        }

        @Override
        public void deleteById(Long id) {
            tools.removeIf(tool -> id != null && id.equals(tool.getId()));
        }
    }

    /** 系统工具替身：执行时回显参数并记录调用次数与模块归属。 */
    static final class RecordingAiTool implements AiAgentTool {
        private final AiAgentToolDescriptor descriptor;
        private final AgentToolRisk risk;
        private final String moduleCode;
        int calls;
        Map<String, Object> lastArguments = Map.of();
        RuntimeException failure;

        RecordingAiTool(String name, String title, String permissionCode, AgentToolRisk risk, String moduleCode) {
            this.descriptor = new AiAgentToolDescriptor(name, title, "测试工具",
                    permissionCode == null ? "" : permissionCode, title, "平台能力", "测试工具",
                    Map.of("text", Map.of("type", "string", "description", "文本")));
            this.risk = risk;
            this.moduleCode = moduleCode;
        }

        @Override
        public AiAgentToolDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public AgentToolRisk risk() {
            return risk;
        }

        @Override
        public String moduleCode() {
            return moduleCode;
        }

        @Override
        public AiAgentToolResult execute(AiAgentToolCall call) {
            if (failure != null) {
                throw failure;
            }
            calls++;
            lastArguments = call.arguments() == null ? Map.of() : Map.copyOf(call.arguments());
            return new AiAgentToolResult(descriptor.name(), "ok", descriptor.permissionCode(),
                    "工具已执行", new LinkedHashMap<>(lastArguments));
        }
    }

    static McpServer mcpServer(String code, String name, boolean enabled, boolean preload, String... toolNames) {
        McpServer server = McpServer.create(name, code);
        server.update(name, code, "测试 MCP 服务", McpTransport.STREAMABLE_HTTP,
                "http://127.0.0.1:1/mcp", Map.of(), enabled, preload, 2000, true);
        List<McpToolDefinition> tools = new ArrayList<>();
        for (String tool : toolNames) {
            tools.add(new McpToolDefinition(tool, "回显工具", "回显输入", Map.of("text", "文本"), "READ", false));
        }
        server.setTools(tools);
        return server;
    }

    /** MCP 工具提供方替身：把工具调用结果固定为回显。 */
    static final class StubMcpProvider implements McpAgentToolProvider {
        private final List<AiAgentTool> tools;

        StubMcpProvider(List<AiAgentTool> tools) {
            this.tools = List.copyOf(tools);
        }

        @Override
        public List<AiAgentTool> tools() {
            return tools;
        }

        @Override
        public AiAgentTool tool(String toolCode) {
            return tools.stream().filter(tool -> tool.descriptor().name().equals(toolCode)).findFirst().orElse(null);
        }
    }

    static AgentPermissionGateway permissionGateway(List<String> granted) {
        return permissionCode -> permissionCode == null || permissionCode.isBlank() || granted.contains(permissionCode);
    }

    static final class AgentPluginToolGatewayStub implements AgentPluginToolGateway {
        final List<AiAgentToolDescriptor> descriptors = new ArrayList<>();
        final Map<String, String> modules = new LinkedHashMap<>();

        void add(String name, String title, String pluginCode) {
            descriptors.add(new AiAgentToolDescriptor(name, title, "插件工具", "plugin:test:use",
                    title, "插件工具", "插件工具", Map.of("text", "文本")));
            modules.put(name, "plugin:" + pluginCode);
        }

        @Override
        public List<AiAgentToolDescriptor> pluginTools() {
            return List.copyOf(descriptors);
        }

        @Override
        public AiAgentTool pluginTool(String toolCode) {
            return null;
        }

        @Override
        public Map<String, String> pluginToolModules() {
            return Map.copyOf(modules);
        }
    }

    static AiToolModuleAppService moduleService(
            List<AiAgentTool> systemTools,
            McpServerRepo mcpRepo,
            AgentToolRepo toolRepo,
            AiToolSettingsRepo settingsRepo,
            CapabilityAppService capabilityService
    ) {
        return moduleService(systemTools, mcpRepo, toolRepo, settingsRepo, capabilityService, List.of(), null);
    }

    static AiToolModuleAppService moduleService(
            List<AiAgentTool> systemTools,
            McpServerRepo mcpRepo,
            AgentToolRepo toolRepo,
            AiToolSettingsRepo settingsRepo,
            CapabilityAppService capabilityService,
            List<AgentPluginToolGateway> pluginGateways,
            McpAgentToolProvider mcpProvider
    ) {
        return new AiToolModuleAppService(
                new FakeObjectProvider<>(systemTools),
                new FakeObjectProvider<>(pluginGateways),
                new FakeObjectProvider<>(List.of()),
                new FakeObjectProvider<>(mcpProvider == null ? List.of() : List.of(mcpProvider)),
                mcpRepo,
                toolRepo,
                settingsRepo,
                capabilityService,
                true,
                "on-demand"
        );
    }

    /** Python 运行器替身：把入参回显为运行结果。 */
    static RuntimeExecutor recordingRuntime() {
        return (script, input) -> new RuntimeExecutionResult(
                "{\"echo\": " + (input == null || input.isBlank() ? "{}" : input) + "}",
                "", 0, 5L, ExecutionStatus.SUCCESS, null);
    }
}
