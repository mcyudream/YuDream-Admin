package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext;

public final class PluginAiToolExecutionScope {
    private static final ThreadLocal<PluginAiExecutionContext> CURRENT = new ThreadLocal<>();
    private PluginAiToolExecutionScope() {}
    public static void set(PluginAiExecutionContext context) { CURRENT.set(context); }
    public static PluginAiExecutionContext current() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
