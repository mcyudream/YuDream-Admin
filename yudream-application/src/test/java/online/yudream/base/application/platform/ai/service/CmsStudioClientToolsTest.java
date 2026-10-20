package online.yudream.base.application.platform.ai.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.ai.service.AiExternalAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.chat.aggregate.ChatSession;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;
import online.yudream.base.domain.platform.chat.repo.ChatSessionRepo;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class CmsStudioClientToolsTest {
    static final List<String> READ = List.of("platform:cms:view", "platform:ai:generate", "platform:chat:use");
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final StandardEnvironment environment = new StandardEnvironment();
    private final ChatSession session = ChatSession.create(7L, "设计会话", ChatScopeType.CONSOLE);
    private CmsStudioClientTools tools() {
        session.setAgentCode("cms-studio"); session.setSpaceSlug("studio-context");
        environment.getSystemProperties().put("yudream.platform.capabilities.cms.enabled", "true");
        CapabilityModuleRepo repo = (CapabilityModuleRepo) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{CapabilityModuleRepo.class}, (proxy, method, args) -> Optional.of(CapabilityModule.builder().code(String.valueOf(args[0])).enabled(enabled.get()).build()));
        ChatSessionRepo sessions = (ChatSessionRepo) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ChatSessionRepo.class}, (proxy, method, args) -> Optional.of(session));
        return new CmsStudioClientTools(new CapabilityAppService(repo, List.of()), environment, sessions);
    }
    private Map<String, Object> args() { return Map.of("contextKey", "studio-context", "documentId", "document", "pageId", "page"); }

    @Test void readScopeNeverGrantsWritesAndDescriptorsExposeOnlyAstTools() {
        var tools = tools();
        try {
            var read = tools.bind(7L, "123", READ, (id, name, args) -> {});
            assertEquals(List.of("cms.studio.read", "cms.studio.validate"), read.stream().map(tool -> tool.descriptor().name()).toList());
            assertTrue(read.stream().allMatch(tool -> tool.risk() == AgentToolRisk.READ && !tool.requiresConfirmation()));
            var write = tools.bind(7L, "123", List.of("*"), (id, name, args) -> {});
            assertEquals(CmsStudioClientTools.NAMES, write.stream().map(tool -> tool.descriptor().name()).toList());
            assertTrue(write.get(1).requiresConfirmation());
            assertEquals("platform:cms:edit", write.get(1).descriptor().permissionCode());
        } finally { environment.getSystemProperties().remove("yudream.platform.capabilities.cms.enabled"); }
    }
    @Test void externalPreparationSendsRealRequestAndRejectsForeignContextOrUnexpectedPayload() {
        var tools = tools();
        try {
            AtomicReference<Map<String, Object>> outbound = new AtomicReference<>();
            var read = (AiExternalAgentTool) tools.bind(7L, "123", READ, (id, name, args) -> { assertEquals("request", id); assertEquals("cms.studio.read", name); outbound.set(args); }).getFirst();
            assertEquals("pending", read.prepare("request", new AiAgentToolCall("cms.studio.read", args())).action());
            assertEquals(args(), outbound.get());
            assertThrows(BizException.class, () -> read.prepare("request", new AiAgentToolCall("cms.studio.read", Map.of("contextKey", "other", "documentId", "document", "pageId", "page"))));
            assertThrows(BizException.class, () -> read.prepare("request", new AiAgentToolCall("cms.studio.read", Map.of("contextKey", "studio-context", "documentId", "document", "pageId", "page", "script", "evil"))));
            enabled.set(false);
            assertThrows(BizException.class, () -> read.prepare("request", new AiAgentToolCall("cms.studio.read", args())));
        } finally { environment.getSystemProperties().remove("yudream.platform.capabilities.cms.enabled"); }
    }
    @Test void projectGateAndRequiredPermissionsFailClosedBeforeExecution() {
        var tools = tools();
        try {
            assertThrows(BizException.class, () -> tools.bind(7L, "123", List.of("platform:chat:use"), (id, name, args) -> fail()));
            environment.getSystemProperties().put("yudream.platform.capabilities.cms.enabled", "false");
            assertThrows(BizException.class, () -> tools.bind(7L, "123", List.of("*"), (id, name, args) -> fail()));
            assertThrows(BizException.class, () -> tools.contextKey(8L, "123"));
            session.setSpaceSlug("x".repeat(241));
            assertThrows(BizException.class, () -> tools.contextKey(7L, "123"));
        } finally { environment.getSystemProperties().remove("yudream.platform.capabilities.cms.enabled"); }
    }
}
