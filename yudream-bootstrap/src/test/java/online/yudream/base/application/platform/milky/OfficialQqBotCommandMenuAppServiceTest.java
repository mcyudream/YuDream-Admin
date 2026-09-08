package online.yudream.base.application.platform.milky;

import online.yudream.base.application.platform.milky.service.OfficialQqBotCommandMenuAppService;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.system.command.service.CommandManageAppService;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.service.MilkyApiGateway;
import online.yudream.base.domain.platform.plugin.enumerate.PluginLifecycleAction;
import online.yudream.base.domain.platform.plugin.event.PluginLifecycleEvent;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfficialQqBotCommandMenuAppServiceTest {

    @Mock
    private CapabilityAppService capabilityAppService;
    @Mock
    private MilkyConnectionRepo connectionRepo;
    @Mock
    private CommandManageAppService commandManageAppService;
    @Mock
    private MilkyApiGateway apiGateway;

    @Test
    void putsOfficialMenuForEnabledOfficialConnections() {
        when(capabilityAppService.enabled("milky")).thenReturn(true);
        MilkyConnection official = officialConnection();
        MilkyConnection milky = MilkyConnection.create("本地", "http://127.0.0.1:3010", "token", "base64", null);
        when(connectionRepo.findEnabled()).thenReturn(List.of(official, milky));
        when(commandManageAppService.list()).thenReturn(List.of(
                new PluginCommandInfo("SYSTEM", "system.menu", "菜单", "菜单", null, "查看可用指令", true),
                new PluginCommandInfo("sample", "sign", "签到", "每日签到", null, "签到", true)));
        when(apiGateway.invoke(any(), eq("get_official_menu"), any())).thenReturn(Map.of("menu", Map.of("items", List.of())));
        when(apiGateway.invoke(any(), eq("get_official_panels"), any()))
                .thenReturn(Map.of("records", List.of(), "is_end", true));
        OfficialQqBotCommandMenuAppService service = service();

        service.syncEnabledOfficialConnections();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(apiGateway).invoke(eq(official.toApiContext()), eq("set_official_menu"), payload.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) payload.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> menu = (Map<String, Object>) body.get("menu");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) menu.get("items");
        assertThat(items).extracting(item -> item.get("send_message")).containsExactly("菜单", "/签到");
    }

    @Test
    void createsGlobalPanelsForGroupAndChannelWhenMissing() {
        when(capabilityAppService.enabled("milky")).thenReturn(true);
        MilkyConnection official = officialConnection();
        when(connectionRepo.findEnabled()).thenReturn(List.of(official));
        when(commandManageAppService.list()).thenReturn(List.of(
                new PluginCommandInfo("SYSTEM", "system.menu", "菜单", "菜单", null, "查看可用指令", true)));
        when(apiGateway.invoke(any(), eq("get_official_menu"), any())).thenReturn(Map.of("menu", Map.of("items", List.of())));
        when(apiGateway.invoke(any(), eq("get_official_panels"), any()))
                .thenReturn(Map.of("records", List.of(), "is_end", true));
        OfficialQqBotCommandMenuAppService service = service();

        service.syncEnabledOfficialConnections();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(apiGateway, times(4)).invoke(eq(official.toApiContext()),
                eq("create_official_panel"), payload.capture());
        assertThat(payload.getAllValues()).extracting(item -> String.valueOf(((Map<?, ?>) item).get("scope")))
                .containsExactly("c2c", "group", "channel", "dm");
        assertThat(payload.getAllValues()).allSatisfy(item -> {
            Map<?, ?> body = (Map<?, ?>) item;
            assertThat(body.get("target_type")).isEqualTo("all");
            @SuppressWarnings("unchecked")
            Map<String, Object> panel = (Map<String, Object>) body.get("panel");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) panel.get("items");
            assertThat(items.getFirst().get("name")).isEqualTo("菜单");
            assertThat(items.getFirst().get("type")).isEqualTo("command");
        });
    }

    @Test
    void updatesExistingManagedPanelInsteadOfCreating() {
        when(capabilityAppService.enabled("milky")).thenReturn(true);
        MilkyConnection official = officialConnection();
        when(connectionRepo.findEnabled()).thenReturn(List.of(official));
        when(commandManageAppService.list()).thenReturn(List.of(
                new PluginCommandInfo("sample", "sign", "签到", "每日签到", null, "签到", true)));
        when(apiGateway.invoke(any(), eq("get_official_menu"), any())).thenReturn(Map.of("menu", Map.of("items", List.of())));
        when(apiGateway.invoke(any(), eq("get_official_panels"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> query = (Map<String, Object>) invocation.getArgument(2);
            return Map.of("records", List.of(Map.of(
                    "panel_id", "p-" + query.get("scope"),
                    "scope", query.get("scope"),
                    "panel", Map.of("remark", "yudream-system-commands", "items", List.of()))),
                    "is_end", true);
        });
        OfficialQqBotCommandMenuAppService service = service();

        service.syncEnabledOfficialConnections();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(apiGateway, times(4)).invoke(eq(official.toApiContext()),
                eq("set_official_panel"), payload.capture());
        verify(apiGateway, never()).invoke(any(), eq("create_official_panel"), any());
        assertThat(payload.getAllValues()).extracting(item -> String.valueOf(((Map<?, ?>) item).get("panel_id")))
                .containsExactly("p-c2c", "p-group", "p-channel", "p-dm");
    }

    @Test
    void skipsWriteWhenPrefetchedSnapshotMatchesDesiredMenu() {
        when(capabilityAppService.enabled("milky")).thenReturn(true);
        MilkyConnection official = officialConnection();
        when(connectionRepo.findEnabled()).thenReturn(List.of(official));
        List<PluginCommandInfo> commands = List.of(
                new PluginCommandInfo("SYSTEM", "system.menu", "菜单", "菜单", null, "查看可用指令", true),
                new PluginCommandInfo("sample", "sign", "签到", "每日签到", null, "签到", true));
        when(commandManageAppService.list()).thenReturn(commands);
        when(apiGateway.invoke(any(), eq("get_official_menu"), any()))
                .thenReturn(online.yudream.base.domain.platform.milky.model.OfficialQqBotCommandMenu.payload(commands));
        when(apiGateway.invoke(any(), eq("get_official_panels"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> query = (Map<String, Object>) invocation.getArgument(2);
            return Map.of("records", List.of(Map.of(
                    "panel_id", "p-" + query.get("scope"),
                    "scope", query.get("scope"),
                    "panel", online.yudream.base.domain.platform.milky.model.OfficialQqBotCommandPanel.panel(commands))),
                    "is_end", true);
        });
        OfficialQqBotCommandMenuAppService service = service();

        service.prefetchRemoteSnapshots();
        service.syncEnabledOfficialConnections();

        verify(apiGateway, never()).invoke(any(), eq("set_official_menu"), any());
        verify(apiGateway, never()).invoke(any(), eq("set_official_panel"), any());
        verify(apiGateway, never()).invoke(any(), eq("create_official_panel"), any());
    }

    @Test
    void skipsWhenCapabilityDisabled() {
        when(capabilityAppService.enabled("milky")).thenReturn(false);
        OfficialQqBotCommandMenuAppService service = service();

        service.syncEnabledOfficialConnections();

        verify(connectionRepo, never()).findEnabled();
        verify(apiGateway, never()).invoke(any(), any(), any());
    }

    @Test
    void pluginEnableTriggersSync() {
        when(capabilityAppService.enabled("milky")).thenReturn(true);
        when(connectionRepo.findEnabled()).thenReturn(List.of());
        OfficialQqBotCommandMenuAppService service = service();

        service.onPluginLifecycle(PluginLifecycleEvent.succeeded("sample", PluginLifecycleAction.ENABLE, "1.0", 1L));

        verify(connectionRepo).findEnabled();
    }

    @Test
    void pluginRestoreDoesNotSyncUntilBootstrapFinishes() {
        OfficialQqBotCommandMenuAppService service = service();

        service.runWithoutLifecycleSync(() ->
                service.onPluginLifecycle(PluginLifecycleEvent.succeeded("sample", PluginLifecycleAction.ENABLE, "1.0", 1L)));

        verify(connectionRepo, never()).findEnabled();
    }

    @Test
    void reusesLocalSnapshotWithoutListingAgainWhenUnchanged() {
        when(capabilityAppService.enabled("milky")).thenReturn(true);
        MilkyConnection official = officialConnection();
        when(connectionRepo.findEnabled()).thenReturn(List.of(official));
        List<PluginCommandInfo> commands = List.of(
                new PluginCommandInfo("sample", "sign", "签到", "每日签到", null, "签到", true));
        when(commandManageAppService.list()).thenReturn(commands);
        when(apiGateway.invoke(any(), eq("get_official_menu"), any()))
                .thenReturn(online.yudream.base.domain.platform.milky.model.OfficialQqBotCommandMenu.payload(commands));
        stubManagedPanels(commands);
        OfficialQqBotCommandMenuAppService service = service();

        service.prefetchRemoteSnapshots();
        service.syncEnabledOfficialConnections();
        service.syncEnabledOfficialConnections();

        verify(apiGateway, times(1)).invoke(any(), eq("get_official_menu"), any());
        verify(apiGateway, times(4)).invoke(any(), eq("get_official_panels"), any());
        verify(apiGateway, never()).invoke(any(), eq("set_official_menu"), any());
        verify(apiGateway, never()).invoke(any(), eq("set_official_panel"), any());
    }

    private OfficialQqBotCommandMenuAppService service() {
        OfficialQqBotCommandMenuAppService service = new OfficialQqBotCommandMenuAppService(
                capabilityAppService, connectionRepo, commandManageAppService, apiGateway);
        service.setDebounceMillis(0);
        return service;
    }

    private MilkyConnection officialConnection() {
        MilkyConnection official = MilkyConnection.create("官方", "official", null, null,
                "app-id", "app-secret", false, null, "base64", null);
        official.setId(8L);
        return official;
    }

    private void stubManagedPanels(List<PluginCommandInfo> commands) {
        when(apiGateway.invoke(any(), eq("get_official_panels"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> query = (Map<String, Object>) invocation.getArgument(2);
            return Map.of("records", List.of(Map.of(
                    "panel_id", "p-" + query.get("scope"),
                    "scope", query.get("scope"),
                    "panel", Map.of("remark", "yudream-system-commands",
                            "items", online.yudream.base.domain.platform.milky.model.OfficialQqBotCommandPanel.items(commands)))),
                    "is_end", true);
        });
    }
}
