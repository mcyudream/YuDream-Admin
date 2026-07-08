package online.yudream.base.plugin.minecraft.domain.aggregate;

import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftServerEndpoint;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftServerSeason;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record MinecraftServer(
        String id,
        String name,
        String descriptionMarkdown,
        boolean enabled,
        int sort,
        List<MinecraftServerEndpoint> endpoints,
        List<MinecraftServerSeason> seasons,
        long createdAt,
        long updatedAt
) {

    public MinecraftServer {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.trim();
        name = requireText(name, "服务器名称不能为空");
        descriptionMarkdown = descriptionMarkdown == null ? "" : descriptionMarkdown.trim();
        if (descriptionMarkdown.length() > 20000) {
            throw new IllegalArgumentException("服务器描述不能超过 20000 个字符");
        }
        endpoints = normalizeEndpoints(endpoints);
        seasons = normalizeSeasons(seasons);
    }

    public static MinecraftServer create(String name, String descriptionMarkdown, boolean enabled, int sort,
                                         List<MinecraftServerEndpoint> endpoints,
                                         List<MinecraftServerSeason> seasons) {
        long now = System.currentTimeMillis();
        return new MinecraftServer(null, name, descriptionMarkdown, enabled, sort, endpoints, seasons, now, now);
    }

    public MinecraftServer update(String name, String descriptionMarkdown, Boolean enabled, Integer sort,
                                  List<MinecraftServerEndpoint> endpoints,
                                  List<MinecraftServerSeason> seasons) {
        return new MinecraftServer(
                id,
                name == null ? this.name : name,
                descriptionMarkdown == null ? this.descriptionMarkdown : descriptionMarkdown,
                enabled == null ? this.enabled : enabled,
                sort == null ? this.sort : sort,
                endpoints == null ? this.endpoints : endpoints,
                seasons == null ? this.seasons : seasons,
                createdAt,
                System.currentTimeMillis()
        );
    }

    public List<MinecraftServerEndpoint> enabledEndpoints() {
        return endpoints.stream()
                .filter(MinecraftServerEndpoint::enabled)
                .sorted(Comparator.comparingInt(MinecraftServerEndpoint::sort))
                .toList();
    }

    public MinecraftServerSeason currentSeason() {
        return seasons.stream().filter(MinecraftServerSeason::current).findFirst().orElse(null);
    }

    private static List<MinecraftServerEndpoint> normalizeEndpoints(List<MinecraftServerEndpoint> endpoints) {
        List<MinecraftServerEndpoint> items = endpoints == null ? List.of() : endpoints.stream()
                .sorted(Comparator.comparingInt(MinecraftServerEndpoint::sort))
                .toList();
        if (items.isEmpty()) {
            throw new IllegalArgumentException("服务器至少需要一条线路");
        }
        boolean hasPrimary = items.stream().anyMatch(MinecraftServerEndpoint::primaryLine);
        if (hasPrimary) {
            return items;
        }
        MinecraftServerEndpoint first = items.get(0);
        return items.stream()
                .map(item -> item.id().equals(first.id())
                        ? new MinecraftServerEndpoint(item.id(), item.name(), item.host(), item.port(), item.edition(), true, item.enabled(), item.sort())
                        : item)
                .toList();
    }

    private static List<MinecraftServerSeason> normalizeSeasons(List<MinecraftServerSeason> seasons) {
        List<MinecraftServerSeason> items = seasons == null ? List.of() : seasons.stream()
                .sorted(Comparator.comparingInt(MinecraftServerSeason::sort))
                .toList();
        boolean currentSeen = false;
        java.util.ArrayList<MinecraftServerSeason> normalized = new java.util.ArrayList<>();
        for (MinecraftServerSeason season : items) {
            boolean current = season.current() && !currentSeen;
            currentSeen = currentSeen || current;
            normalized.add(season.withCurrent(current));
        }
        return List.copyOf(normalized);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String text = value.trim();
        if (text.length() > 64) {
            throw new IllegalArgumentException("服务器名称不能超过 64 个字符");
        }
        return text;
    }
}
