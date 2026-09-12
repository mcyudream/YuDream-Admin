package online.yudream.base.infra.platform.plugin.bootstrap;

import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketSource;
import online.yudream.base.domain.platform.plugin.enumerate.MarketSourceType;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketSourceRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginMarketSourceInitializerTest {

    @Mock
    private PluginMarketSourceRepo pluginMarketSourceRepo;

    @Test
    void seedsBuiltinLocalSourceWhenMissing() {
        when(pluginMarketSourceRepo.findByCode("default")).thenReturn(Optional.empty());
        when(pluginMarketSourceRepo.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        new PluginMarketSourceInitializer(pluginMarketSourceRepo).run(new DefaultApplicationArguments());

        ArgumentCaptor<PluginMarketSource> captor = ArgumentCaptor.forClass(PluginMarketSource.class);
        verify(pluginMarketSourceRepo).save(captor.capture());
        PluginMarketSource saved = captor.getValue();
        assertEquals("default", saved.getCode());
        assertEquals("本机插件市场", saved.getName());
        assertEquals(MarketSourceType.LOCAL, saved.getType());
        assertTrue(saved.builtIn());
        assertNull(saved.getRootUrl());
    }

    @Test
    void rewritesLegacyNexusBuiltinToLocal() {
        PluginMarketSource existing = PluginMarketSource.builder()
                .code("default")
                .name("官方插件市场")
                .rootUrl("https://nexus.yudream.online/repository/plugin-store-releases/index.json")
                .builtIn(true)
                .enabled(true)
                .build();
        when(pluginMarketSourceRepo.findByCode("default")).thenReturn(Optional.of(existing));
        when(pluginMarketSourceRepo.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        new PluginMarketSourceInitializer(pluginMarketSourceRepo).run(new DefaultApplicationArguments());

        ArgumentCaptor<PluginMarketSource> captor = ArgumentCaptor.forClass(PluginMarketSource.class);
        verify(pluginMarketSourceRepo).save(captor.capture());
        PluginMarketSource saved = captor.getValue();
        assertEquals(MarketSourceType.LOCAL, saved.getType());
        assertEquals("本机插件市场", saved.getName());
        assertNull(saved.getRootUrl());
    }
}
