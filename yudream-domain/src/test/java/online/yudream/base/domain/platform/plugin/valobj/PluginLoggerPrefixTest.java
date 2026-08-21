package online.yudream.base.domain.platform.plugin.valobj;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginLoggerPrefixTest {

    @Test
    void derivesPrefixFromMainClassPackageSegment() {
        assertEquals("online.yudream.base.plugin.wordle",
                PluginLoggerPrefix.of("online.yudream.base.plugin.wordle.bootstrap.WordlePlugin", "wordle"));
        // 插件编码带连字符时包段与编码不同，以 mainClass 包段为准
        assertEquals("online.yudream.base.plugin.aichatbot",
                PluginLoggerPrefix.of("online.yudream.base.plugin.aichatbot.bootstrap.AiChatbotPlugin", "ai-chatbot"));
    }

    @Test
    void fallsBackToRootPlusCode() {
        assertEquals("online.yudream.base.plugin.wordle", PluginLoggerPrefix.of(null, "wordle"));
        // mainClass 未遵循包约定
        assertEquals("online.yudream.base.plugin.demo",
                PluginLoggerPrefix.of("com.example.demo.DemoPlugin", "demo"));
        // 根包后没有包段（非法 mainClass，防御）
        assertEquals("online.yudream.base.plugin.demo",
                PluginLoggerPrefix.of("online.yudream.base.plugin.", "demo"));
    }
}
