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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityAppServiceTest {

    @Test
    void retainsExistingNeo4jPasswordWhenUpdateOmitsItAndNeverReturnsIt() {
        CapabilityModule module = CapabilityModule.builder()
                .code("neo4j")
                .name("Neo4j")
                .type(CapabilityType.GRAPH)
                .enabled(false)
                .config(new HashMap<>(Map.of("uri", "bolt://old:7687", "username", "neo4j", "password", "old-secret", "database", "neo4j")))
                .build();
        CapabilityModuleRepo repo = mock(CapabilityModuleRepo.class);
        when(repo.findByCode("neo4j")).thenReturn(Optional.of(module));
        when(repo.save(any(CapabilityModule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CapabilityProvider provider = new TestNeo4jProvider();
        CapabilityAppService service = new CapabilityAppService(repo, List.of(provider));
        CapabilityConfigUpdateCmd command = new CapabilityConfigUpdateCmd();
        command.setCode("neo4j");
        command.setConfig(Map.of("uri", "bolt://new:7687", "username", "neo4j", "password", "", "database", "neo4j"));

        CapabilityDTO result = service.updateConfig(command);

        assertThat(module.getConfig()).containsEntry("password", "old-secret").containsEntry("uri", "bolt://new:7687");
        assertThat(result.getConfig()).doesNotContainKey("password");
        assertThat(result.getSecretConfigured()).containsEntry("password", true);
    }

    @Test
    void restoresOtherDescriptorsWhenNeo4jCredentialCannotBeRecovered() {
        CapabilityModule module = CapabilityModule.builder()
                .code("neo4j")
                .name("Neo4j")
                .type(CapabilityType.GRAPH)
                .enabled(true)
                .config(Map.of("password", "v1:unreadable"))
                .build();
        CapabilityModuleRepo repo = mock(CapabilityModuleRepo.class);
        when(repo.findByCode("neo4j")).thenReturn(Optional.of(module));
        when(repo.findAll()).thenReturn(List.of(module));
        when(repo.save(any(CapabilityModule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CapabilityProvider provider = mock(CapabilityProvider.class);
        when(provider.descriptor()).thenReturn(new CapabilityDescriptor("neo4j", "Neo4j", CapabilityType.GRAPH, "", "", 1, Map.of()));
        org.mockito.Mockito.doThrow(new IllegalStateException("凭据无法解密")).when(provider).enable(module.getConfig());
        CapabilityAppService service = new CapabilityAppService(repo, List.of(provider));

        service.restoreEnabledProviders();

        assertThat(module.enabled()).isFalse();
        verify(provider).disable();
    }

    private static final class TestNeo4jProvider implements CapabilityProvider {
        private final AtomicReference<Map<String, String>> config = new AtomicReference<>();

        @Override
        public CapabilityDescriptor descriptor() {
            return new CapabilityDescriptor("neo4j", "Neo4j", CapabilityType.GRAPH, "", "", 1, Map.of());
        }

        @Override
        public CapabilityHealth health() {
            return CapabilityHealth.disabled("disabled");
        }

        @Override
        public void enable(Map<String, String> config) {
            this.config.set(config);
        }

        @Override
        public void disable() {
        }

        @Override
        public CapabilityTestResult test(String message) {
            return CapabilityTestResult.success(message);
        }
    }
}
