package online.yudream.base.application.platform.plugin.devmode;

import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.domain.platform.plugin.event.PluginDevReloadRequested;
import online.yudream.base.domain.platform.plugin.event.PluginLifecycleEvent;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginRuntimeAssets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginDevModeReloadListenerTest {

    private final PluginAppService pluginAppService = mock(PluginAppService.class);
    private final PluginRuntimeGateway runtimeGateway = mock(PluginRuntimeGateway.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final PluginDevModeReloadListener listener =
            new PluginDevModeReloadListener(pluginAppService, runtimeGateway, eventPublisher);

    @Test
    void skipsReloadAndPublishesNothingWhenDevModeDisabled() {
        when(runtimeGateway.devModeEnabled()).thenReturn(false);

        listener.onReloadRequested(PluginDevReloadRequested.ofRegister("demo"));
        listener.onReloadRequested(PluginDevReloadRequested.of("demo"));

        verify(pluginAppService, never()).reloadDevPlugin(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void reloadsAndBroadcastsLifecycleEventWhenDevModeEnabled() {
        when(runtimeGateway.devModeEnabled()).thenReturn(true);
        when(runtimeGateway.runtimeAssets("demo")).thenReturn(PluginRuntimeAssets.unloaded("demo"));

        listener.onReloadRequested(PluginDevReloadRequested.ofRegister("demo"));

        verify(pluginAppService).reloadDevPlugin("demo");
        ArgumentCaptor<PluginLifecycleEvent> captor = ArgumentCaptor.forClass(PluginLifecycleEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals("demo", captor.getValue().pluginCode());
        assertTrue(captor.getValue().success());
    }

    @Test
    void publishesFailureEventWhenReloadThrows() {
        when(runtimeGateway.devModeEnabled()).thenReturn(true);
        when(runtimeGateway.runtimeAssets("demo")).thenReturn(PluginRuntimeAssets.unloaded("demo"));
        when(pluginAppService.reloadDevPlugin("demo")).thenThrow(new RuntimeException("开发产物不存在"));

        listener.onReloadRequested(PluginDevReloadRequested.of("demo"));

        ArgumentCaptor<PluginLifecycleEvent> captor = ArgumentCaptor.forClass(PluginLifecycleEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertFalse(captor.getValue().success());
        assertTrue(captor.getValue().errorMessage().contains("开发产物不存在"));
    }
}
