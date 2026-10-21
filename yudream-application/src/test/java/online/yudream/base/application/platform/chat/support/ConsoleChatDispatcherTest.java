package online.yudream.base.application.platform.chat.support;

import online.yudream.base.domain.platform.ai.service.HarnessAgentGateway;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ConsoleChatDispatcherTest {
    @Test
    void studioProfileNormalizesAndOnlyAllowsAstToolsWithDraftPrompt() {
        AtomicReference<HarnessRunRequest> seen = new AtomicReference<>();
        HarnessAgentGateway gateway = (request, delta, reasoning, tool) -> {
            seen.set(request); return new HarnessRunResult("", "", AiUsage.empty(), List.of());
        };
        CapabilityModuleRepo modules = (CapabilityModuleRepo) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{CapabilityModuleRepo.class},
                (proxy, method, args) -> Optional.of(CapabilityModule.builder().code("ai").enabled(true).config(Map.of()).build()));
        var dispatcher = new ConsoleChatDispatcher(gateway, modules, null);
        dispatcher.studioTools(new online.yudream.base.application.platform.ai.service.CmsStudioClientTools(null, null, null) {
            @Override public void ensureAccess(List<String> permissions) { assertEquals(List.of("*"), permissions); }
            @Override public String contextKey(Long userId, String sessionId) { assertEquals("123", sessionId); return "bound"; }
        });
        dispatcher.dispatch(new ChatDispatchContext(7L, "123", "优化页面", null, null, " CMS-STUDIO ", "bound", List.of(), List.of(), List.of("*"), ignored -> {}, ignored -> {}, ignored -> {}));
        assertEquals("cms-studio", seen.get().agentCode());
        assertEquals(online.yudream.base.application.platform.ai.service.CmsStudioClientTools.NAMES.stream().map(name -> name.replace('.', '_')).toList(), seen.get().allowedToolNames());
        assertTrue(seen.get().allowedToolNames().containsAll(List.of("cms_studio_outline", "cms_studio_check", "cms_studio_apply")));
        assertTrue(seen.get().systemPrompt().contains("不保存、不发布"));
        assertTrue(seen.get().systemPrompt().contains("expectedRevision"));
        assertFalse(seen.get().allowedToolNames().contains("write_file"));
    }

    @Test
    void retiredWorkspaceProfilesAreRejectedBeforeInvokingTheModel() {
        AtomicReference<HarnessRunRequest> seen = new AtomicReference<>();
        HarnessAgentGateway gateway = (request,delta,reasoning,tool) -> {
            seen.set(request); return new HarnessRunResult("", "", AiUsage.empty(), List.of());
        };
        CapabilityModule ai = CapabilityModule.builder().code("ai").enabled(true).config(Map.of()).build();
        CapabilityModuleRepo modules = (CapabilityModuleRepo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CapabilityModuleRepo.class},(proxy,method,args)->Optional.of(ai));
        List<String> actualPermissions = List.of("platform:chat:use");
        // 站点设置服务在本用例中不参与断言；dispatcher 的 siteName() 对空依赖有回落保护。
        var dispatcher = new ConsoleChatDispatcher(gateway, modules, null);
        for (String code : List.of("cms-builder", "cms", " CMS-BUILDER ")) {
            var error = assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                    () -> dispatcher.dispatch(new ChatDispatchContext(7L,"session","帮助编辑",null,null,code,null,List.of(),List.of(),
                            actualPermissions,ignored->{},ignored->{},ignored->{})));
            assertTrue(error.getMessage().contains("旧站点工作区已移除"));
        }
        assertNull(seen.get());
    }
}
