package online.yudream.base.application.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.assembler.AgentModelCatalogParser;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AiModelCatalogAppServiceTest {
    @Test
    void listsChatModelsWithAgentAndWikiDisabled() {
        var models = service(true).availableModels();
        assertEquals(1, models.size());
        assertEquals("chat-model", models.getFirst().modelCode());
        assertTrue(models.getFirst().configured());
    }

    @Test
    void disabledAiCannotExposeModelCatalog() {
        assertThrows(BizException.class, () -> service(false).availableModels());
    }

    private AiModelCatalogAppService service(boolean enabled) {
        var config = Map.of("providers", """
                [{"code":"chat","apiKey":"test-only","models":["chat-model"]},
                 {"code":"embed","usage":"embedding","apiKey":"test-only","models":["embedding-model"]}]
                """);
        CapabilityModuleRepo modules = (CapabilityModuleRepo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CapabilityModuleRepo.class}, (proxy, method, args) -> {
                    assertEquals("ai", args[0], "模型目录只能查询 AI 能力");
                    return Optional.of(CapabilityModule.builder().code("ai").enabled(enabled).config(config).build());
                });
        return new AiModelCatalogAppService(modules, null, new AgentModelCatalogParser(new ObjectMapper()));
    }
}
