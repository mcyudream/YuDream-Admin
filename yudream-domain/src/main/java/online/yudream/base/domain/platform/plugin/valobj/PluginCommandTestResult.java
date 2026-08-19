package online.yudream.base.domain.platform.plugin.valobj;

/**
 * 开发者工具指令模拟执行结果：在指定插件作用域内同步触发指令处理器并捕获异常，
 * 无需真实 QQ 事件即可调试插件指令。
 */
public record PluginCommandTestResult(
        String pluginCode,
        String command,
        boolean matched,
        boolean success,
        String errorMessage,
        Long durationMs
) {
    public static PluginCommandTestResult notMatched(String pluginCode, String command, Long durationMs) {
        return new PluginCommandTestResult(pluginCode, command, false, false, "插件未注册该指令", durationMs);
    }

    public static PluginCommandTestResult succeeded(String pluginCode, String command, Long durationMs) {
        return new PluginCommandTestResult(pluginCode, command, true, true, null, durationMs);
    }

    public static PluginCommandTestResult failed(String pluginCode, String command, String errorMessage, Long durationMs) {
        return new PluginCommandTestResult(pluginCode, command, true, false, errorMessage, durationMs);
    }
}
