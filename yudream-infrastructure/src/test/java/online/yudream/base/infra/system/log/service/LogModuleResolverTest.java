package online.yudream.base.infra.system.log.service;

import online.yudream.base.domain.platform.milky.model.OfficialQqBotEventCatalog;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogModuleResolverTest {

    private final LogModuleResolver resolver = new LogModuleResolver();

    @Test
    void mdcOverrideWinsOverLoggerKeyword() {
        String module = resolver.resolve(
                "online.yudream.base.infra.platform.milky.service.MilkyEventSystemLogger",
                Map.of(LogModuleResolver.MDC_MODULE, OfficialQqBotEventCatalog.CATEGORY_GROUP_MESSAGE));
        assertEquals("QQ 群消息", module);
    }

    @Test
    void milkyLoggersFallBackToPlatformModule() {
        assertEquals("QQ 消息平台", resolver.resolve(
                "online.yudream.base.infra.platform.milky.official.OfficialQqBotEventGateway"));
        assertEquals("QQ 消息平台", resolver.resolve(
                "online.yudream.base.infra.platform.milky.service.MilkyConnectionRuntime"));
        assertEquals("入站邮箱", resolver.resolve(
                "online.yudream.base.infra.platform.mail.service.ImapsInboundMailboxGateway"));
    }

    @Test
    void knownGroupsSurfaceQqEventModulesFirst() {
        LogModuleResolver.ModuleGroup qq = resolver.knownModuleGroups().getFirst();
        assertEquals("QQ 机器人", qq.label());
        assertTrue(qq.modules().contains("QQ 群消息"));
        assertTrue(qq.modules().contains("QQ 消息平台"));
        assertTrue(resolver.knownModules().contains("QQ 互动"));
        assertTrue(resolver.knownModuleGroups().stream()
                .filter(group -> "平台".equals(group.label()))
                .findFirst()
                .orElseThrow()
                .modules()
                .contains("入站邮箱"));
    }
}
