package online.yudream.base.plugin.spi.system.ai;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface PluginAiService {
    CompletionStage<PluginAiChatResponse> chat(PluginAiChatRequest request);
    java.util.List<PluginAiToolDescriptor> tools();
    java.util.List<PluginAiProviderOption> providers();
    java.util.List<PluginAiAgentOption> agents();
    CompletionStage<PluginAiChatResponse> runAgent(String agentCode, PluginAiChatRequest request);

    /** 立即返回持久运行 ID；前端通过 SDK ai.runs 观察真实增量。 */
    default PluginAiRunHandle startChat(PluginAiChatRequest request) {
        throw new UnsupportedOperationException("当前 AI 服务不支持持久任务");
    }

    default PluginAiRunHandle startAgent(String agentCode, PluginAiChatRequest request) {
        throw new UnsupportedOperationException("当前 AI 服务不支持持久 Agent 任务");
    }

    /** 原调用者恢复任务；不允许读取其他用户或无执行上下文的系统任务。 */
    default PluginAiRunSnapshot getRun(String runId, PluginAiExecutionContext executionContext) {
        throw new UnsupportedOperationException("当前 AI 服务不支持持久任务查询");
    }

    /**
     * 真流式对话：onDelta 只接收模型正文增量，推理与生命周期进度不得混入正文。
     * 未提供流式实现时明确失败，禁止将一次性结果整段回放冒充流式。
     */
    default CompletionStage<PluginAiChatResponse> chatStream(PluginAiChatRequest request, Consumer<String> onDelta) {
        return chatStream(request, onDelta, null);
    }

    /**
     * 流式对话 + 工具调用回调：每次工具执行完成回调 onTool（逐题入库等场景可实时感知进度）。
     * 插件写工具需在 executionContext.grantedWriteToolNames 中逐个点名授权，否则宿主只放行 READ 工具。
     */
    default CompletionStage<PluginAiChatResponse> chatStream(
            PluginAiChatRequest request,
            Consumer<String> onDelta,
            Consumer<PluginAiToolResult> onTool
    ) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("当前 AI 服务未实现真流式对话"));
    }

    default CompletionStage<PluginAiChatResponse> runAgentStream(
            String agentCode,
            PluginAiChatRequest request,
            Consumer<String> onDelta
    ) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("当前 AI 服务未实现真流式 Agent 运行"));
    }
}
