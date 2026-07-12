package online.yudream.base.plugin.spi.system.ai;

public record PluginAiChatMessage(String role, String content) {
    public boolean assistant() {
        return "assistant".equalsIgnoreCase(role);
    }
}
