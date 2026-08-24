package online.yudream.base.application.platform.capability.service;

import online.yudream.base.application.platform.capability.cmd.CapabilityConfigUpdateCmd;
import online.yudream.base.application.platform.capability.dto.CapabilityDTO;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilityAppServiceNeo4jConfigTest {

    @Test
    void blankNeo4jPasswordRetainsStoredSecretAndResponseDoesNotExposeIt() throws Exception {
        CapabilityModule module = CapabilityModule.builder()
                .code("neo4j")
                .name("Neo4j 图数据库")
                .type(CapabilityType.GRAPH)
                .enabled(true)
                .config(Map.of("uri", "bolt://old:7687", "username", "neo4j", "password", "stored-secret", "database", "neo4j"))
                .build();
        CapabilityModuleRepo repository = mock(CapabilityModuleRepo.class);
        when(repository.findByCode("neo4j")).thenReturn(Optional.of(module));
        when(repository.save(module)).thenReturn(module);
        AtomicReference<Map<String, String>> enabledConfig = new AtomicReference<>();
        CapabilityAppService service = new CapabilityAppService(repository, List.of(neo4jProvider(enabledConfig)));
        CapabilityConfigUpdateCmd command = new CapabilityConfigUpdateCmd();
        command.setCode("neo4j");
        command.setConfig(Map.of("uri", "bolt://new:7687", "username", "admin", "password", "", "database", "graph"));

        CapabilityDTO result = service.updateConfig(command);

        assertThat(enabledConfig.get()).containsEntry("password", "stored-secret")
                .containsEntry("uri", "bolt://new:7687").containsEntry("username", "admin").containsEntry("database", "graph");
        assertThat(result.getConfig()).doesNotContainKey("password");
        assertThat(secretConfigured(result)).containsEntry("password", true);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Boolean> secretConfigured(CapabilityDTO result) throws Exception {
        Method getter = CapabilityDTO.class.getMethod("getSecretConfigured");
        return (Map<String, Boolean>) getter.invoke(result);
    }

    private static CapabilityProvider neo4jProvider(AtomicReference<Map<String, String>> enabledConfig) {
        return new CapabilityProvider() {
            @Override
            public CapabilityDescriptor descriptor() {
                return new CapabilityDescriptor("neo4j", "Neo4j 图数据库", CapabilityType.GRAPH, "", "", 0, Map.of());
            }

            @Override
            public CapabilityHealth health() {
                return CapabilityHealth.enabled("已启用", Map.of());
            }

            @Override
            public void enable(Map<String, String> config) {
                enabledConfig.set(Map.copyOf(config));
            }

            @Override
            public void disable() {
            }

            @Override
            public CapabilityTestResult test(String message) {
                return CapabilityTestResult.success(message);
            }
        };
    }
}
