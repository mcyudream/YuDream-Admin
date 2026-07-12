package online.yudream.base.plugin.spi.system.ai;

public interface PluginAiTool {
    PluginAiToolDescriptor descriptor();
    PluginAiToolResult execute(PluginAiExecutionContext context, PluginAiToolCall call);
}
