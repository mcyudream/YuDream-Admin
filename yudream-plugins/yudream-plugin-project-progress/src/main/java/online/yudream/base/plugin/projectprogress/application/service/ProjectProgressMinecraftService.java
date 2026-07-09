package online.yudream.base.plugin.projectprogress.application.service;

import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectMinecraftEvidence;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectMinecraftPolicy;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.minecraft.PluginMinecraftPlayerActivity;
import online.yudream.base.plugin.spi.system.minecraft.PluginMinecraftService;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;

import java.util.List;
import java.util.Optional;

public class ProjectProgressMinecraftService {

    private static final String MINECRAFT_PLUGIN = "minecraft-server";
    private static final int SCAN_PAGE_SIZE = 200;

    private final FrameworkServices framework;

    public ProjectProgressMinecraftService(FrameworkServices framework) {
        this.framework = framework;
    }

    public boolean ready() {
        return minecraft().isPresent();
    }

    public ProjectMinecraftEvidence requireEvidence(ProjectMinecraftPolicy policy, String userId) {
        if (policy == null || !policy.enabled()) {
            throw new IllegalArgumentException("该项目未启用 Minecraft 在线时长打卡");
        }
        PluginMinecraftPlayerActivity activity = matchActivity(policy.serverId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("未找到当前用户的 Minecraft 在线记录"));
        long effective = policy.includeAfk()
                ? activity.totalOnlineMillis()
                : Math.max(0, activity.totalOnlineMillis() - activity.totalAfkMillis());
        long requiredMillis = policy.requiredOnlineMinutes() * 60_000L;
        if (effective < requiredMillis) {
            throw new IllegalArgumentException("Minecraft 在线时长未达到自动打卡要求");
        }
        return new ProjectMinecraftEvidence(policy.serverId(), activity.playerId(), activity.playerName(),
                activity.totalOnlineMillis(), activity.totalAfkMillis(), effective);
    }

    private Optional<PluginMinecraftPlayerActivity> matchActivity(String serverId, String userId) {
        PluginUserProfile user = userProfile(userId).orElse(null);
        String username = user == null ? "" : text(user.username());
        String nickname = user == null ? "" : text(user.nickname());
        return allActivities(serverId).stream()
                .filter(activity -> matches(activity, userId, username, nickname))
                .findFirst();
    }

    private boolean matches(PluginMinecraftPlayerActivity activity, String userId, String username, String nickname) {
        String playerId = text(activity.playerId());
        String playerName = text(activity.playerName());
        return playerId.equalsIgnoreCase(text(userId))
                || playerName.equalsIgnoreCase(text(userId))
                || (!username.isBlank() && playerName.equalsIgnoreCase(username))
                || (!nickname.isBlank() && playerName.equalsIgnoreCase(nickname));
    }

    private List<PluginMinecraftPlayerActivity> allActivities(String serverId) {
        PluginMinecraftService service = minecraft()
                .orElseThrow(() -> new IllegalArgumentException("Minecraft 服务插件未启用"));
        List<PluginMinecraftPlayerActivity> result = new java.util.ArrayList<>();
        int page = 1;
        while (true) {
            List<PluginMinecraftPlayerActivity> batch = service.minecraftPlayerActivities(serverId, page, SCAN_PAGE_SIZE);
            result.addAll(batch);
            if (batch.size() < SCAN_PAGE_SIZE) {
                return result;
            }
            page++;
        }
    }

    private Optional<PluginMinecraftService> minecraft() {
        return framework == null ? Optional.empty() : framework.extension(MINECRAFT_PLUGIN, PluginMinecraftService.class);
    }

    private Optional<PluginUserProfile> userProfile(String userId) {
        if (framework == null || framework.users() == null || userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        try {
            return framework.users().findById(Long.parseLong(userId.trim()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
