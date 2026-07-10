package online.yudream.base.infra.platform.plugin;

import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.infra.platform.plugin.mapper.PluginModuleInfraMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluginModuleMenuInitializationTest {

    @Test
    void persistsMenuInitializationMarkerAcrossRepositoryMapping() {
        PluginModule module = PluginModule.builder()
                .code("minecraft-server")
                .build();
        module.markMenusInitialized();

        PluginModule restored = PluginModuleInfraMapper.toDomain(PluginModuleInfraMapper.toDataObj(module));

        assertThat(restored.menusInitialized()).isTrue();
    }
}
