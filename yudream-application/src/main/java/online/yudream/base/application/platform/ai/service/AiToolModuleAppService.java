package online.yudream.base.application.platform.ai.service;

import online.yudream.base.application.platform.ai.dto.AiToolModuleDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.agent.service.AgentPluginToolGateway;
import online.yudream.base.domain.platform.ai.aggregate.AiToolSettings;
import online.yudream.base.domain.platform.ai.repo.AiToolSettingsRepo;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiToolLoadState;
import online.yudream.base.domain.platform.ai.valobj.AiToolModulePlan;
import online.yudream.base.domain.platform.ai.valobj.AiToolModules;
import online.yudream.base.domain.platform.mcp.aggregate.McpServer;
import online.yudream.base.domain.platform.mcp.repo.McpServerRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统工具模块服务：模块目录、全局/模块开关、运行期模块计划与 load_tools 元工具载荷。
 * <p>
 * 模块是系统工具的唯一分组与开关口径：
 * <ul>
 *   <li>宿主工具按工具名前缀（web/cms/wiki/ui...），load_tools 归「工具目录」；</li>
 *   <li>插件工具归 plugin:&lt;pluginCode&gt;；</li>
 *   <li>MCP 工具归 mcp:&lt;serverCode&gt;，「常驻」开关决定是否按需加载。</li>
 * </ul>
 */
@Service
public class AiToolModuleAppService {

    private static final int MAX_LOAD_PAYLOAD_TOOLS = 60;
    private static final int MAX_LOAD_DESCRIPTION = 160;
    /** MCP 工具的统一调用权限码（随 MCP 菜单按钮注册）。 */
    public static final String PERMISSION_CODE_MCP_USE = "platform:mcp:server:use";

    private final ObjectProvider<AiAgentTool> systemTools;
    private final ObjectProvider<AgentPluginToolGateway> pluginToolGateways;
    private final ObjectProvider<PluginRuntimeGateway> pluginRuntime;
    private final McpServerRepo mcpServerRepo;
    private final AgentToolRepo toolRepo;
    private final AiToolSettingsRepo settingsRepo;
    private final online.yudream.base.application.platform.capability.service.CapabilityAppService capabilityAppService;
    private final boolean configEnabled;
    private final boolean onDemandLoading;

    public AiToolModuleAppService(
            ObjectProvider<AiAgentTool> systemTools,
            ObjectProvider<AgentPluginToolGateway> pluginToolGateways,
            ObjectProvider<PluginRuntimeGateway> pluginRuntime,
            McpServerRepo mcpServerRepo,
            AgentToolRepo toolRepo,
            AiToolSettingsRepo settingsRepo,
            online.yudream.base.application.platform.capability.service.CapabilityAppService capabilityAppService,
            @Value("${yudream.platform.agent.system-tools-enabled:true}") boolean configEnabled,
            @Value("${yudream.platform.agent.tool-loading:on-demand}") String toolLoadingMode
    ) {
        this.systemTools = systemTools;
        this.pluginToolGateways = pluginToolGateways;
        this.pluginRuntime = pluginRuntime;
        this.mcpServerRepo = mcpServerRepo;
        this.toolRepo = toolRepo;
        this.settingsRepo = settingsRepo;
        this.capabilityAppService = capabilityAppService;
        this.configEnabled = configEnabled;
        this.onDemandLoading = !"eager".equalsIgnoreCase(toolLoadingMode == null ? "" : toolLoadingMode.trim());
    }

    public AiToolSettings settings() {
        return settingsRepo.load();
    }

    /** 总开关：配置项与持久开关同时开启才生效。 */
    public boolean systemToolsEnabled() {
        return configEnabled && settings().isSystemEnabled();
    }

    /** 是否按需加载模式；eager 模式下所有模块常驻，load_tools 不注册。 */
    public boolean onDemandLoading() {
        return onDemandLoading;
    }

    public boolean configEnabled() {
        return configEnabled;
    }

    // ------------------------------------------------------------------ 管理端视图

    public AiToolModuleDTO catalog() {
        ensureManageable();
        AiToolSettings settings = settings();
        Map<String, AiToolModules.ModuleMeta> metas = new LinkedHashMap<>();
        Map<String, List<AiToolModuleDTO.Tool>> toolsByModule = new LinkedHashMap<>();
        for (AiToolModules.ModuleMeta meta : AiToolModules.platformModuleList()) {
            metas.put(meta.code(), meta);
        }
        for (McpServer server : mcpServerRepo.findAll()) {
            AiToolModules.ModuleMeta meta = AiToolModules.mcpModule(server.getCode(), server.getName(),
                    server.getDescription(), server.isPreload());
            metas.put(meta.code(), meta);
            List<AiToolModuleDTO.Tool> mcpTools = new ArrayList<>();
            for (var definition : server.toolsOrEmpty()) {
                mcpTools.add(new AiToolModuleDTO.Tool(
                        AiToolModules.mcpToolName(server.getCode(), definition.name()),
                        definition.title(),
                        definition.description(),
                        PERMISSION_CODE_MCP_USE,
                        definition.risk(),
                        "mcp",
                        definition.requiresConfirmation(),
                        server.isEnabled()
                ));
            }
            toolsByModule.put(meta.code(), mcpTools);
        }
        for (AgentPluginToolGateway gateway : pluginToolGateways) {
            Map<String, String> pluginModules = gateway.pluginToolModules();
            for (AiAgentToolDescriptor descriptor : gateway.pluginTools()) {
                if (descriptor == null || descriptor.name() == null) {
                    continue;
                }
                String moduleCode = pluginModules.getOrDefault(descriptor.name(), AiToolModules.PLUGIN_PREFIX + "plugin");
                metas.putIfAbsent(moduleCode, pluginModuleMeta(moduleCode));
                toolsByModule.computeIfAbsent(moduleCode, ignored -> new ArrayList<>())
                        .add(descriptorTool(descriptor, "plugin", AgentToolRisk.WRITE.name(), true));
            }
        }
        List<AiAgentTool> holders = new ArrayList<>();
        for (AiAgentTool tool : systemTools) {
            if (tool != null && tool.descriptor() != null && tool.descriptor().name() != null) {
                holders.add(tool);
            }
        }
        holders.sort(Comparator.comparing(tool -> tool.descriptor().name()));
        for (AiAgentTool tool : holders) {
            String moduleCode = moduleCodeOf(tool);
            metas.putIfAbsent(moduleCode, AiToolModules.platformModule(moduleCode));
            toolsByModule.computeIfAbsent(moduleCode, ignored -> new ArrayList<>()).add(toolView(tool));
        }
        List<AiToolModuleDTO.Tool> customTools = customTools();
        if (!customTools.isEmpty()) {
            metas.put(AiToolModules.MODULE_CUSTOM, AiToolModules.customModule());
            toolsByModule.put(AiToolModules.MODULE_CUSTOM, customTools);
        }
        // load_tools 元工具按运行期注册，不进 Spring 容器；目录里补一条只读说明，便于管理端理解模块机制。
        if (configEnabled) {
            List<AiToolModuleDTO.Tool> metaTools = new ArrayList<>(
                    toolsByModule.getOrDefault(AiToolModules.MODULE_META, List.of()));
            AiAgentToolDescriptor loadTools = loadToolsDescriptor();
            metaTools.add(new AiToolModuleDTO.Tool(
                    loadTools.name(),
                    displayName(loadTools),
                    loadTools.description(),
                    "",
                    AgentToolRisk.READ.name(),
                    "system",
                    false,
                    true
            ));
            toolsByModule.put(AiToolModules.MODULE_META, metaTools);
        }
        List<AiToolModuleDTO.Module> modules = new ArrayList<>();
        for (AiToolModules.ModuleMeta meta : metas.values()) {
            List<AiToolModuleDTO.Tool> tools = toolsByModule.getOrDefault(meta.code(), List.of());
            boolean enabled = settings.isModuleEnabled(meta.code());
            modules.add(new AiToolModuleDTO.Module(
                    meta.code(),
                    meta.name(),
                    meta.description(),
                    meta.icon(),
                    source(meta.code()),
                    meta.alwaysOn(),
                    enabled,
                    !AiToolModules.MODULE_META.equals(meta.code()),
                    onDemandLoading && !meta.alwaysOn(),
                    tools.size(),
                    tools
            ));
        }
        return new AiToolModuleDTO(systemToolsEnabled(), configEnabled, onDemandLoading, modules);
    }

    public AiToolModuleDTO updateSystemEnabled(boolean enabled) {
        ensureManageable();
        AiToolSettings settings = settings();
        settings.updateSystemEnabled(enabled);
        settingsRepo.save(settings);
        return catalog();
    }

    public AiToolModuleDTO updateModuleEnabled(String moduleCode, boolean enabled) {
        ensureManageable();
        if (moduleCode == null || moduleCode.isBlank()) {
            throw new BizException("模块编码不能为空");
        }
        AiToolSettings settings = settings();
        settings.updateModuleEnabled(moduleCode, enabled);
        settingsRepo.save(settings);
        return catalog();
    }

    // ------------------------------------------------------------------ 运行期计划

    /** 运行期模块计划：registeredTools 的键为模型侧安全名，值为本次真正注册的工具。 */
    public AiToolModulePlan plan(Map<String, AiAgentTool> registeredTools) {
        AiToolSettings settings = settings();
        Map<String, AiToolModules.ModuleMeta> metas = new LinkedHashMap<>();
        Map<String, AiToolModulePlan.ToolEntry> entries = new LinkedHashMap<>();
        Map<String, List<String>> moduleToolNames = new LinkedHashMap<>();
        Set<String> disabledModules = new LinkedHashSet<>();
        if (registeredTools != null) {
            registeredTools.forEach((safeName, tool) -> {
                if (safeName == null || safeName.isBlank() || tool == null) {
                    return;
                }
                AiAgentToolDescriptor descriptor = tool.descriptor();
                String moduleCode = moduleCodeOf(tool);
                metas.putIfAbsent(moduleCode, moduleMeta(moduleCode));
                if (!settings.isModuleEnabled(moduleCode)) {
                    disabledModules.add(moduleCode);
                    return;
                }
                entries.put(safeName, new AiToolModulePlan.ToolEntry(
                        safeName,
                        descriptor.name(),
                        displayName(descriptor),
                        descriptor.description() == null ? "" : descriptor.description(),
                        descriptor.permissionCode() == null ? "" : descriptor.permissionCode(),
                        tool.risk() == null ? AgentToolRisk.WRITE.name() : tool.risk().name(),
                        moduleCode,
                        tool.requiresConfirmation()
                ));
                moduleToolNames.computeIfAbsent(moduleCode, ignored -> new ArrayList<>()).add(safeName);
            });
        }
        return new AiToolModulePlan(configEnabled && settings.isSystemEnabled(), onDemandLoading,
                AiToolModules.LOAD_TOOLS, metas, entries, moduleToolNames, disabledModules);
    }

    /** 模块闸门：关闭的模块（含总开关）不允许被应用引用或模型调用。 */
    public void ensureToolAvailable(AiAgentTool tool) {
        if (tool == null || tool.descriptor() == null) {
            return;
        }
        ensureModuleOpen(tool.moduleCode(), moduleCodeOf(tool), tool.descriptor().name());
    }

    /** 非抛错版本：工具在当前总开关/模块开关下是否可用（列表与发布校验用）。 */
    public boolean toolAllowed(AiAgentTool tool) {
        if (tool == null || tool.descriptor() == null) {
            return false;
        }
        if (!systemToolsEnabled()) {
            return false;
        }
        return settings().isModuleEnabled(moduleCodeOf(tool));
    }

    /** 非抛错版本：按模块编码判断模块是否开放。 */
    public boolean moduleOpen(String moduleCode) {
        return systemToolsEnabled() && settings().isModuleEnabled(moduleCode);
    }

    /** 工作流路径按工具编码复核宿主工具模块（工具编码不存在时不抛错，交由后续解析处理）。 */
    public void ensureToolCodeAllowed(String toolCode) {
        if (toolCode == null || toolCode.isBlank()) {
            return;
        }
        ensureModuleOpen(null, AiToolModules.platformToolModule(toolCode), toolCode);
    }

    private void ensureModuleOpen(String declaredModule, String moduleCode, String toolCode) {
        String module = declaredModule == null || declaredModule.isBlank() ? moduleCode : declaredModule.trim();
        if (!configEnabled || !settings().isSystemEnabled()) {
            throw new BizException("系统工具总开关已关闭，工具不可用：" + toolCode);
        }
        if (!settings().isModuleEnabled(module)) {
            AiToolModules.ModuleMeta meta = moduleMeta(module);
            throw new BizException("工具模块已关闭：" + meta.name() + "（" + module + "）");
        }
    }

    // ------------------------------------------------------------------ load_tools 元工具

    public AiAgentToolDescriptor loadToolsDescriptor() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("module", Map.of(
                "type", "string",
                "description", "要加载的工具模块编码；省略时返回模块目录"
        ));
        schema.put("keyword", Map.of(
                "type", "string",
                "description", "可选，按名称过滤模块或工具"
        ));
        return new AiAgentToolDescriptor(
                AiToolModules.LOAD_TOOLS,
                "加载工具模块",
                "查看系统工具模块目录并按模块加载。系统工具按模块提供：先查看目录，再按需加载对应模块，"
                        + "加载后该模块的工具即可直接调用。",
                "",
                "加载工具模块",
                "平台能力",
                "按模块加载系统工具，不额外授予工具权限。",
                schema
        );
    }

    /** load_tools 载荷：不带 module 返回目录，带 module 时加载并返回该模块工具清单。 */
    public Map<String, Object> loadToolsResult(AiToolModulePlan plan, AiToolLoadState state, Map<String, Object> arguments) {
        String moduleCode = arguments == null ? null : text(arguments.get("module"));
        String keyword = arguments == null ? null : text(arguments.get("keyword"));
        if (moduleCode == null || moduleCode.isBlank()) {
            return moduleCatalogPayload(plan, state, keyword);
        }
        String code = moduleCode.trim();
        AiToolModules.ModuleMeta meta = plan.module(code);
        if (meta == null || !plan.moduleAllowed(code)) {
            Map<String, Object> payload = moduleCatalogPayload(plan, state, keyword);
            payload.put("success", false);
            payload.put("message", "模块不存在或已关闭：" + code);
            return payload;
        }
        boolean already = state != null && state.loaded(code);
        if (state != null) {
            state.load(code);
        }
        List<Map<String, Object>> tools = new ArrayList<>();
        for (AiToolModulePlan.ToolEntry entry : plan.moduleEntries(code)) {
            tools.add(toolPayload(entry));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("module", code);
        payload.put("name", meta.name());
        payload.put("description", truncate(meta.description(), MAX_LOAD_DESCRIPTION));
        payload.put("alreadyLoaded", already);
        payload.put("tools", tools);
        payload.put("message", (already ? "该模块的工具已加载，可直接调用：" : "模块已加载，可以直接调用这些工具：") + joinNames(tools));
        return payload;
    }

    private Map<String, Object> moduleCatalogPayload(AiToolModulePlan plan, AiToolLoadState state, String keyword) {
        String filter = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Map<String, Object>> modules = new ArrayList<>();
        for (String code : plan.moduleCodes()) {
            AiToolModules.ModuleMeta meta = plan.module(code);
            if (meta == null || !plan.moduleAllowed(code)) {
                continue;
            }
            List<AiToolModulePlan.ToolEntry> entries = plan.moduleEntries(code);
            if (!filter.isBlank() && !matchesModule(meta, entries, filter)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("module", code);
            item.put("name", meta.name());
            item.put("description", truncate(meta.description(), MAX_LOAD_DESCRIPTION));
            item.put("alwaysOn", meta.alwaysOn());
            item.put("loaded", meta.alwaysOn() || (state != null && state.loaded(code)));
            item.put("toolCount", entries.size());
            item.put("tools", entries.stream().limit(MAX_LOAD_PAYLOAD_TOOLS)
                    .map(AiToolModulePlan.ToolEntry::safeName).toList());
            modules.add(item);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("modules", modules);
        payload.put("loadedModules", state == null ? List.of() : List.copyOf(state.loadedModules()));
        payload.put("message", modules.isEmpty()
                ? "没有匹配的工具模块。"
                : "以上是工具模块目录。需要某个模块的工具时，调用 load_tools 并传入对应 module 编码，"
                        + "加载后即可直接调用该模块的工具。");
        return payload;
    }

    private boolean matchesModule(AiToolModules.ModuleMeta meta, List<AiToolModulePlan.ToolEntry> entries, String filter) {
        if (meta.code().toLowerCase().contains(filter) || meta.name().toLowerCase().contains(filter)) {
            return true;
        }
        return entries.stream().anyMatch(entry -> entry.safeName().toLowerCase().contains(filter)
                || entry.toolName().toLowerCase().contains(filter)
                || entry.title().toLowerCase().contains(filter));
    }

    private Map<String, Object> toolPayload(AiToolModulePlan.ToolEntry entry) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", entry.safeName());
        item.put("title", entry.title());
        item.put("description", truncate(entry.description(), MAX_LOAD_DESCRIPTION));
        if (entry.requiresConfirmation()) {
            item.put("requiresConfirmation", true);
        }
        return item;
    }

    private String joinNames(List<Map<String, Object>> tools) {
        return tools.stream().map(item -> String.valueOf(item.get("name")))
                .collect(java.util.stream.Collectors.joining("、"));
    }

    // ------------------------------------------------------------------ 组装辅助

    /** 工具所属模块编码：工具显式声明优先，否则按工具名前缀推导。 */
    public String moduleCodeOf(AiAgentTool tool) {
        String declared = tool.moduleCode();
        if (declared != null && !declared.isBlank()) {
            return declared.trim();
        }
        String name = tool.descriptor() == null ? null : tool.descriptor().name();
        return name == null || name.isBlank() ? AiToolModules.MODULE_GENERAL : AiToolModules.platformToolModule(name);
    }

    private AiToolModuleDTO.Tool toolView(AiAgentTool tool) {
        AiAgentToolDescriptor descriptor = tool.descriptor();
        return new AiToolModuleDTO.Tool(
                descriptor.name(),
                displayName(descriptor),
                descriptor.description() == null ? "" : descriptor.description(),
                descriptor.permissionCode() == null ? "" : descriptor.permissionCode(),
                tool.risk() == null ? AgentToolRisk.WRITE.name() : tool.risk().name(),
                "system",
                tool.requiresConfirmation(),
                true
        );
    }

    private AiToolModuleDTO.Tool descriptorTool(AiAgentToolDescriptor descriptor, String source, String risk, boolean enabled) {
        return new AiToolModuleDTO.Tool(
                descriptor.name(),
                displayName(descriptor),
                descriptor.description() == null ? "" : descriptor.description(),
                descriptor.permissionCode() == null ? "" : descriptor.permissionCode(),
                risk,
                source,
                true,
                enabled
        );
    }

    private List<AiToolModuleDTO.Tool> customTools() {
        List<AiToolModuleDTO.Tool> tools = new ArrayList<>();
        for (AgentTool tool : toolRepo.page(null, 1, 1000).getRecords()) {
            tools.add(new AiToolModuleDTO.Tool(
                    tool.getCode(),
                    tool.getName(),
                    tool.getDescription() == null ? "" : tool.getDescription(),
                    tool.getPermissionCode() == null ? "" : tool.getPermissionCode(),
                    AgentToolRisk.WRITE.name(),
                    "python",
                    true,
                    Boolean.TRUE.equals(tool.getEnabled())
            ));
        }
        return tools;
    }

    /** 管理端读写（模块目录与开关）要求 Agent 应用编排能力已启用；运行期 plan 不受此闸门影响。 */
    private void ensureManageable() {
        if (capabilityAppService != null) {
            capabilityAppService.ensureEnabled("agent", "系统工具模块");
        }
    }

    private String source(String moduleCode) {
        if (AiToolModules.mcpModuleCode(moduleCode)) {
            return "mcp";
        }
        if (AiToolModules.pluginModuleCode(moduleCode)) {
            return "plugin";
        }
        if (AiToolModules.MODULE_CUSTOM.equals(moduleCode)) {
            return "python";
        }
        return "system";
    }

    private AiToolModules.ModuleMeta moduleMeta(String moduleCode) {
        if (AiToolModules.mcpModuleCode(moduleCode)) {
            String serverCode = AiToolModules.mcpServerCode(moduleCode);
            return mcpServerRepo.findByCode(serverCode)
                    .map(server -> AiToolModules.mcpModule(server.getCode(), server.getName(),
                            server.getDescription(), server.isPreload()))
                    .orElseGet(() -> AiToolModules.mcpModule(serverCode, serverCode, "", false));
        }
        if (AiToolModules.pluginModuleCode(moduleCode)) {
            return pluginModuleMeta(moduleCode);
        }
        return AiToolModules.platformModule(moduleCode);
    }

    private AiToolModules.ModuleMeta pluginModuleMeta(String moduleCode) {
        String code = AiToolModules.pluginModuleCode(moduleCode)
                ? moduleCode.substring(AiToolModules.PLUGIN_PREFIX.length())
                : moduleCode;
        for (PluginDescriptorInfo descriptor : pluginDescriptors()) {
            if (code.equalsIgnoreCase(descriptor.code())) {
                return AiToolModules.pluginModule(code,
                        descriptor.name() == null || descriptor.name().isBlank() ? code : descriptor.name(),
                        descriptor.description(), descriptor.icon());
            }
        }
        return AiToolModules.pluginModule(code, code, "", null);
    }

    private List<PluginDescriptorInfo> pluginDescriptors() {
        PluginRuntimeGateway gateway = pluginRuntime == null ? null : pluginRuntime.getIfAvailable();
        if (gateway == null) {
            return List.of();
        }
        try {
            List<PluginDescriptorInfo> descriptors = gateway.discover();
            return descriptors == null ? List.of() : descriptors;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private String displayName(AiAgentToolDescriptor descriptor) {
        return descriptor.title() == null || descriptor.title().isBlank() ? descriptor.name() : descriptor.title();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
