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
    void selectingCmsWorkspaceNeverGrantsHostToolPermission() {
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
        dispatcher.dispatch(new ChatDispatchContext(7L,"session","帮助编辑",null,null,"cms-builder",null,List.of(),List.of(),
                actualPermissions,ignored->{},ignored->{},ignored->{}));
        assertEquals("cms-builder",seen.get().agentCode());
        assertEquals(actualPermissions,seen.get().permissionCodes());
        assertFalse(seen.get().permissionCodes().contains("platform:ai:tool:cms-chrome-style"));
    }
}
