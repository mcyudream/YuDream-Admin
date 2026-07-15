package online.yudream.base.application.platform.agent.service;

public final class BuiltinAgentCodes {
    public static final String CMS_BUILDER = "builtin-cms-builder";
    public static final String LEGACY_GROUP_CHATBOT = "builtin-group-chatbot";
    public static final String AGUI_CARD = "builtin-agui-card";

    private BuiltinAgentCodes() {
    }

    public static boolean isPluginManaged(String code) {
        return LEGACY_GROUP_CHATBOT.equals(code);
    }
}
