package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import online.yudream.base.application.platform.ai.service.AiToolModuleAppService;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiToolLoadState;
import online.yudream.base.domain.platform.ai.valobj.AiToolModulePlan;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * load_tools 元工具：查看系统工具模块目录并按模块加载工具。
 * 模块加载只改变模型可见的工具 schema（由 {@link PerCallToolFilterMiddleware} 在下一轮推理生效），
 * 不授予任何权限；工具执行仍按权限快照与模块开关 fail-closed 复核。
 */
public class LoadToolsTool extends ToolBase {

    private final AiToolModuleAppService moduleService;
    private final AiToolModulePlan plan;

    public LoadToolsTool(AiToolModuleAppService moduleService, AiToolModulePlan plan) {
        super(ToolBase.builder()
                .name(moduleService.loadToolsDescriptor().name())
                .description(moduleService.loadToolsDescriptor().description())
                .inputSchema(schema(moduleService.loadToolsDescriptor()))
                .readOnly(true)
                .concurrencySafe(true));
        this.moduleService = moduleService;
        this.plan = plan;
    }

    private static Map<String, Object> schema(AiAgentToolDescriptor descriptor) {
        Map<String, Object> definition = descriptor.inputSchemaDefinition();
        if (definition != null && !definition.isEmpty()) {
            return definition;
        }
        return ToolSchemas.normalizeSimple(descriptor.inputSchema());
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        RuntimeContext context = param.getRuntimeContext();
        AiToolLoadState state = context == null ? null : context.get(AiToolLoadState.class);
        Map<String, Object> payload;
        try {
            payload = moduleService.loadToolsResult(plan, state, param.getInput());
        } catch (RuntimeException exception) {
            payload = Map.of("success", false, "message", "工具模块加载失败：" + exception.getMessage());
        }
        String json = ToolSchemas.resultJson(payload);
        record(param, payload);
        return Mono.just(ToolResultBlock.builder()
                .id(param.getToolUseBlock().getId())
                .name(param.getToolUseBlock().getName())
                .output(TextBlock.builder().text(json).build())
                .state(ToolResultState.SUCCESS)
                .build());
    }

    private void record(ToolCallParam param, Map<String, Object> payload) {
        RuntimeContext context = param.getRuntimeContext();
        if (context == null) {
            return;
        }
        HarnessToolCollector collector = context.get(HarnessToolCollector.class);
        if (collector == null) {
            return;
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>(payload);
        data.put("_toolCallId", param.getToolUseBlock().getId());
        data.put("_nativeToolState", ToolResultState.SUCCESS.name());
        boolean success = !Boolean.FALSE.equals(payload.get("success"));
        collector.record(new AiAgentToolResult(
                OnlineToolName.META_TOOL,
                success ? "load_tools" : "error",
                "",
                String.valueOf(payload.getOrDefault("message", "")),
                data));
    }

    /** 与平台工具目录一致的记录名（模型侧名字同为 load_tools）。 */
    private static final class OnlineToolName {
        private static final String META_TOOL = online.yudream.base.domain.platform.ai.valobj.AiToolModules.LOAD_TOOLS;
    }
}
