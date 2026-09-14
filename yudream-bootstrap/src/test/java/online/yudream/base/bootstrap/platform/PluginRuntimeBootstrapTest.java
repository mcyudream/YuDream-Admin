package online.yudream.base.bootstrap.platform;

import online.yudream.base.application.platform.milky.service.OfficialQqBotCommandMenuAppService;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PluginRuntimeBootstrapTest {

    private static final int WEB_SERVER_START_PHASE = Integer.MAX_VALUE - 1024;

    @Mock
    private PluginAppService pluginAppService;

    @Mock
    private OfficialQqBotCommandMenuAppService officialCommandMenuAppService;

    private PluginRuntimeBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new PluginRuntimeBootstrap(pluginAppService, officialCommandMenuAppService);
    }

    @Test
    void startRestoresPluginsBeforeWebServerPhaseWithoutTouchingOfficialMenu() {
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(officialCommandMenuAppService).runWithoutLifecycleSync(any());

        bootstrap.start();

        InOrder order = inOrder(officialCommandMenuAppService, pluginAppService);
        order.verify(officialCommandMenuAppService).runWithoutLifecycleSync(any());
        order.verify(pluginAppService).restoreEnabledPlugins();
        verify(officialCommandMenuAppService, never()).prefetchRemoteSnapshots();
        verify(officialCommandMenuAppService, never()).syncEnabledOfficialConnections();
        assertThat(bootstrap.isRunning()).isTrue();
        assertThat(bootstrap.getPhase()).isLessThan(WEB_SERVER_START_PHASE);
    }

    @Test
    void readyEventPrefetchesThenSyncsOfficialMenus() {
        bootstrap.onApplicationEvent((ApplicationReadyEvent) null);

        InOrder order = inOrder(officialCommandMenuAppService);
        order.verify(officialCommandMenuAppService).prefetchRemoteSnapshots();
        order.verify(officialCommandMenuAppService).syncEnabledOfficialConnections();
        verify(pluginAppService, never()).restoreEnabledPlugins();
    }
}
