package online.yudream.base.application.platform.milky.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.system.command.service.CommandManageAppService;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.model.OfficialQqBotCommandMenu;
import online.yudream.base.domain.platform.milky.model.OfficialQqBotCommandPanel;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.service.MilkyApiGateway;
import online.yudream.base.domain.platform.plugin.enumerate.PluginLifecycleAction;
import online.yudream.base.domain.platform.plugin.event.PluginLifecycleEvent;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 官方 QQ 机器人指令 UI 与系统/插件指令绑定：
 * 单聊底部自定义菜单（PUT /v2/menu）+ 单聊/群聊/文字子频道/频道私信指令面板（/v2/panels）。
 * 启动时先把远端菜单/面板拉到本地快照，插件全部加载后再按差异分类创建/更新；
 * 重载期间与本地快照对比，30s 合并写入，菜单无变动则跳过官方调用。
 * 面板查询 30 QPM、创建/修改 10 QPM。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfficialQqBotCommandMenuAppService {
    static final long DEFAULT_DEBOUNCE_MILLIS = 30_000;

    private static final Set<PluginLifecycleAction> SYNC_ACTIONS = EnumSet.of(
            PluginLifecycleAction.ENABLE,
            PluginLifecycleAction.DISABLE,
            PluginLifecycleAction.UNLOAD,
            PluginLifecycleAction.RELOAD
    );

    private final CapabilityAppService capabilityAppService;
    private final MilkyConnectionRepo connectionRepo;
    private final CommandManageAppService commandManageAppService;
    private final MilkyApiGateway apiGateway;
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final AtomicInteger suppressLifecycle = new AtomicInteger();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "official-qqbot-command-sync");
        thread.setDaemon(true);
        return thread;
    });
    private final Object scheduleLock = new Object();
    private volatile ScheduledFuture<?> pendingSync;
    private volatile long debounceMillis = DEFAULT_DEBOUNCE_MILLIS;

    @EventListener
    public void onPluginLifecycle(PluginLifecycleEvent event) {
        if (event == null || !event.success() || event.action() == null || !SYNC_ACTIONS.contains(event.action())) {
            return;
        }
        if (suppressLifecycle.get() > 0) {
            return;
        }
        requestSync();
    }

    public void runWithoutLifecycleSync(Runnable action) {
        suppressLifecycle.incrementAndGet();
        try {
            action.run();
        } finally {
            suppressLifecycle.decrementAndGet();
        }
    }

    /**
     * 启动恢复前拉取远端菜单/面板到本地快照，后续差异计算不再重复打查询接口。
     */
    public void prefetchRemoteSnapshots() {
        if (!capabilityAppService.enabled("milky")) {
            return;
        }
        for (MilkyConnection connection : officialConnections()) {
            prefetchConnection(connection);
        }
    }

    public void requestSync() {
        if (debounceMillis <= 0) {
            syncEnabledOfficialConnections();
            return;
        }
        synchronized (scheduleLock) {
            if (pendingSync != null && !pendingSync.isDone()) {
                pendingSync.cancel(false);
            }
            pendingSync = scheduler.schedule(() -> {
                try {
                    syncEnabledOfficialConnections();
                } catch (Exception exception) {
                    log.warn("Official QQ bot command UI delayed sync failed", exception);
                }
            }, debounceMillis, TimeUnit.MILLISECONDS);
        }
    }

    public void syncEnabledOfficialConnections() {
        if (!capabilityAppService.enabled("milky")) {
            return;
        }
        List<MilkyConnection> connections = officialConnections();
        if (connections.isEmpty()) {
            return;
        }
        List<PluginCommandInfo> commands = commandManageAppService.list();
        for (MilkyConnection connection : connections) {
            Snapshot snapshot = snapshotOf(connection);
            syncMenu(connection, snapshot, commands);
            for (String scope : OfficialQqBotCommandPanel.SCOPES) {
                syncPanel(connection, snapshot, scope, commands);
            }
        }
    }

    public void setDebounceMillis(long debounceMillis) {
        this.debounceMillis = debounceMillis;
    }

    private void syncMenu(MilkyConnection connection, Snapshot snapshot, List<PluginCommandInfo> commands) {
        try {
            if (OfficialQqBotCommandMenu.sameItems(snapshot.menu(), commands)) {
                log.debug("Official QQ bot command menu unchanged: connectionId={}", connection.getId());
                return;
            }
            Object payload = OfficialQqBotCommandMenu.payload(commands);
            apiGateway.invoke(connection.toApiContext(), "set_official_menu", payload);
            snapshot.menu(payload);
            log.info("Official QQ bot command menu synced: connectionId={}, name={}",
                    connection.getId(), connection.getName());
        } catch (Exception exception) {
            log.warn("Official QQ bot command menu sync failed: connectionId={}, name={}",
                    connection.getId(), connection.getName(), exception);
        }
    }

    private void syncPanel(MilkyConnection connection, Snapshot snapshot, String scope, List<PluginCommandInfo> commands) {
        try {
            OfficialQqBotCommandPanel.Snapshot remote = snapshot.panel(scope);
            if (remote != null && OfficialQqBotCommandPanel.samePanel(remote.panel(), commands)) {
                log.debug("Official QQ bot command panel unchanged: connectionId={}, scope={}",
                        connection.getId(), scope);
                return;
            }
            if (remote == null || remote.panelId() == null || remote.panelId().isBlank()) {
                Object created = apiGateway.invoke(connection.toApiContext(), "create_official_panel",
                        OfficialQqBotCommandPanel.createPayload(scope, commands));
                String panelId = OfficialQqBotCommandPanel.panelId(created);
                snapshot.panel(scope, new OfficialQqBotCommandPanel.Snapshot(panelId, OfficialQqBotCommandPanel.panel(commands)));
                log.info("Official QQ bot command panel created: connectionId={}, scope={}",
                        connection.getId(), scope);
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>(OfficialQqBotCommandPanel.updatePayload(commands));
            payload.put("panel_id", remote.panelId());
            apiGateway.invoke(connection.toApiContext(), "set_official_panel", payload);
            snapshot.panel(scope, new OfficialQqBotCommandPanel.Snapshot(remote.panelId(), OfficialQqBotCommandPanel.panel(commands)));
            log.info("Official QQ bot command panel updated: connectionId={}, scope={}, panelId={}",
                    connection.getId(), scope, remote.panelId());
        } catch (Exception exception) {
            log.warn("Official QQ bot command panel sync failed: connectionId={}, scope={}",
                    connection.getId(), scope, exception);
        }
    }

    private Snapshot prefetchConnection(MilkyConnection connection) {
        Snapshot snapshot = new Snapshot();
        try {
            snapshot.menu(apiGateway.invoke(connection.toApiContext(), "get_official_menu", null));
        } catch (Exception exception) {
            log.warn("Official QQ bot command menu prefetch failed: connectionId={}", connection.getId(), exception);
        }
        for (String scope : OfficialQqBotCommandPanel.SCOPES) {
            OfficialQqBotCommandPanel.Snapshot panel = findManagedPanel(connection, scope);
            if (panel != null) {
                snapshot.panel(scope, panel);
            }
        }
        snapshots.put(snapshotKey(connection), snapshot);
        return snapshot;
    }

    private Snapshot snapshotOf(MilkyConnection connection) {
        Snapshot existing = snapshots.get(snapshotKey(connection));
        return existing == null ? prefetchConnection(connection) : existing;
    }

    private OfficialQqBotCommandPanel.Snapshot findManagedPanel(MilkyConnection connection, String scope) {
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("scope", scope);
            query.put("limit", 50);
            if (cursor != null && !cursor.isBlank()) {
                query.put("cursor", cursor);
            }
            try {
                Object response = apiGateway.invoke(connection.toApiContext(), "get_official_panels", query);
                OfficialQqBotCommandPanel.Snapshot snapshot = OfficialQqBotCommandPanel.findManagedSnapshot(response);
                if (snapshot != null) {
                    return snapshot;
                }
                if (OfficialQqBotCommandPanel.listEnded(response)) {
                    return null;
                }
                cursor = OfficialQqBotCommandPanel.nextCursor(response);
                if (cursor == null || cursor.isBlank()) {
                    return null;
                }
            } catch (Exception exception) {
                log.warn("Official QQ bot command panel list failed: connectionId={}, scope={}",
                        connection.getId(), scope, exception);
                return null;
            }
        }
        return null;
    }

    private List<MilkyConnection> officialConnections() {
        return connectionRepo.findEnabled().stream()
                .filter(MilkyConnection::official)
                .toList();
    }

    private static String snapshotKey(MilkyConnection connection) {
        return String.valueOf(connection.getId());
    }

    static final class Snapshot {
        private Object menu;
        private final Map<String, OfficialQqBotCommandPanel.Snapshot> panels = new ConcurrentHashMap<>();

        Object menu() {
            return menu;
        }

        void menu(Object menu) {
            this.menu = menu;
        }

        OfficialQqBotCommandPanel.Snapshot panel(String scope) {
            return panels.get(scope);
        }

        void panel(String scope, OfficialQqBotCommandPanel.Snapshot snapshot) {
            if (snapshot == null) {
                panels.remove(scope);
                return;
            }
            panels.put(scope, snapshot);
        }
    }
}
