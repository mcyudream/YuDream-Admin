package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketSourceCreateCmd;
import online.yudream.base.application.platform.plugin.service.PluginMarketPublicationAppService;
import online.yudream.base.application.platform.plugin.service.PluginMarketSourceAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketSource;
import online.yudream.base.domain.platform.plugin.enumerate.MarketSourceType;
import online.yudream.base.domain.platform.plugin.port.PluginStoreGateway;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketSourceRepo;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketSourceSnapshotRepo;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreCatalogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginMarketSourceAppServiceTest {

    @Mock
    private PluginStoreGateway pluginStoreGateway;
    @Mock
    private PluginMarketSourceRepo pluginMarketSourceRepo;
    @Mock
    private PluginMarketSourceSnapshotRepo pluginMarketSourceSnapshotRepo;
    @Mock
    private CapabilityAppService capabilityAppService;
    @Mock
    private PluginMarketPublicationAppService pluginMarketPublicationAppService;

    private PluginMarketSourceAppService service;

    @BeforeEach
    void setUp() {
        service = new PluginMarketSourceAppService(pluginStoreGateway, pluginMarketSourceRepo,
                pluginMarketSourceSnapshotRepo, capabilityAppService, pluginMarketPublicationAppService);
        ReflectionTestUtils.setField(service, "projectGateEnabled", true);
    }

    @Test
    void createRejectsLocalTypeAndAcceptsV2() {
        PluginMarketSourceCreateCmd local = new PluginMarketSourceCreateCmd();
        local.setCode("evil");
        local.setName("Evil");
        local.setType("LOCAL");
        local.setRootUrl("https://example.test/api/public/plugin-market");
        assertThrows(BizException.class, () -> service.create(local));
        verify(pluginMarketSourceRepo, never()).save(any());

        PluginMarketSourceCreateCmd v2 = new PluginMarketSourceCreateCmd();
        v2.setCode("community");
        v2.setName("社区");
        v2.setType("V2_API");
        v2.setRootUrl("https://community.example.test/api/public/plugin-market");
        when(pluginMarketSourceRepo.findByCode("community")).thenReturn(Optional.empty());
        when(pluginMarketSourceRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service.create(v2);
        assertEquals(MarketSourceType.V2_API, dto.getType());
        assertEquals("community", dto.getCode());
    }

    @Test
    void enabledSourceCatalogsReadsLocalPublicationsWithoutGateway() {
        PluginMarketSource local = PluginMarketSource.builder()
                .id(1L)
                .code("default")
                .name("本机插件市场")
                .type(MarketSourceType.LOCAL)
                .enabled(true)
                .builtIn(true)
                .build();
        when(pluginMarketSourceRepo.findAll()).thenReturn(List.of(local));
        when(capabilityAppService.enabled("plugin-market-source")).thenReturn(true);
        PluginStoreCatalogEntry entry = new PluginStoreCatalogEntry("demo", "local:demo", null, List.of(), List.of());
        when(pluginMarketPublicationAppService.localCatalogEntries()).thenReturn(List.of(entry));

        var catalogs = service.enabledSourceCatalogs();
        assertEquals(1, catalogs.size());
        assertEquals("demo", catalogs.getFirst().snapshot().entries().getFirst().code());
        verify(pluginStoreGateway, never()).fetchCatalog(any());
        verify(pluginMarketSourceSnapshotRepo, never()).save(any());
    }

    @Test
    void createRemoteDoesNotRequireCapability() {
        PluginMarketSourceCreateCmd v2 = new PluginMarketSourceCreateCmd();
        v2.setCode("community");
        v2.setName("社区");
        v2.setType("V2_API");
        v2.setRootUrl("https://community.example.test/api/public/plugin-market");
        when(pluginMarketSourceRepo.findByCode("community")).thenReturn(Optional.empty());
        when(pluginMarketSourceRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service.create(v2);

        assertEquals("community", dto.getCode());
        verify(capabilityAppService, never()).ensureEnabled(any(), any());
    }

    @Test
    void listHidesLocalSourceWhenCapabilityInactive() {
        when(capabilityAppService.enabled("plugin-market-source")).thenReturn(false);
        PluginMarketSource local = PluginMarketSource.builder()
                .id(1L)
                .code("default")
                .name("本机插件市场")
                .type(MarketSourceType.LOCAL)
                .enabled(true)
                .builtIn(true)
                .build();
        PluginMarketSource remote = PluginMarketSource.builder()
                .id(2L)
                .code("community")
                .name("社区")
                .type(MarketSourceType.V2_API)
                .rootUrl("https://community.example.test/api/public/plugin-market")
                .enabled(true)
                .builtIn(false)
                .build();
        when(pluginMarketSourceRepo.findAll()).thenReturn(List.of(local, remote));
        when(pluginMarketSourceSnapshotRepo.findAll()).thenReturn(List.of());

        var result = service.list();

        assertEquals(1, result.size());
        assertEquals("community", result.getFirst().getCode());
        verify(capabilityAppService, never()).ensureEnabled(any(), any());
    }

    @Test
    void enabledSourceCatalogsSkipsLocalWhenCapabilityInactive() {
        when(capabilityAppService.enabled("plugin-market-source")).thenReturn(false);
        PluginMarketSource local = PluginMarketSource.builder()
                .id(1L)
                .code("default")
                .name("本机插件市场")
                .type(MarketSourceType.LOCAL)
                .enabled(true)
                .builtIn(true)
                .build();
        when(pluginMarketSourceRepo.findAll()).thenReturn(List.of(local));

        assertEquals(0, service.enabledSourceCatalogs().size());
        verify(pluginMarketPublicationAppService, never()).localCatalogEntries();
    }
}
