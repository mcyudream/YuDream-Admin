package online.yudream.base.plugin.minecraft.infrastructure.service;

import online.yudream.base.plugin.minecraft.application.service.MinecraftServerAppService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MinecraftStatusScheduler implements AutoCloseable {

    private static final long INITIAL_DELAY_SECONDS = 30;
    private static final long REFRESH_INTERVAL_MINUTES = 10;

    private final MinecraftServerAppService appService;
    private ScheduledExecutorService executor;

    public MinecraftStatusScheduler(MinecraftServerAppService appService) {
        this.appService = appService;
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "minecraft-status-refresh");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::refreshSafely, INITIAL_DELAY_SECONDS, REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public synchronized void close() {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        executor = null;
    }

    private void refreshSafely() {
        try {
            appService.refreshEnabledServers();
        } catch (RuntimeException ignored) {
        }
    }
}
