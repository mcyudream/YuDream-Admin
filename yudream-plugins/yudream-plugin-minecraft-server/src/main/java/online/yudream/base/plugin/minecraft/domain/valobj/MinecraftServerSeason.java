package online.yudream.base.plugin.minecraft.domain.valobj;

import java.util.UUID;

public record MinecraftServerSeason(
        String id,
        String name,
        String description,
        Long startedAt,
        Long endedAt,
        boolean current,
        int sort
) {

    public MinecraftServerSeason {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.trim();
        name = requireText(name, "周目名称不能为空");
        description = trimToNull(description);
        if (startedAt != null && endedAt != null && endedAt < startedAt) {
            throw new IllegalArgumentException("周目结束时间不能早于开始时间");
        }
    }

    public MinecraftServerSeason withCurrent(boolean value) {
        return new MinecraftServerSeason(id, name, description, startedAt, endedAt, value, sort);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
