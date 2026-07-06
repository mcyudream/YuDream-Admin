package online.yudream.base.plugin.skin.domain.enumerate;

import java.util.Locale;

public enum SkinTextureType {
    STEVE("skin", "default"),
    ALEX("skin", "slim"),
    CAPE("cape", "default");

    private final String yggdrasilType;
    private final String model;

    SkinTextureType(String yggdrasilType, String model) {
        this.yggdrasilType = yggdrasilType;
        this.model = model;
    }

    public String yggdrasilType() {
        return yggdrasilType;
    }

    public String model() {
        return model;
    }

    public static SkinTextureType from(String value) {
        if (value == null || value.isBlank()) {
            return STEVE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("alex".equals(normalized) || "slim".equals(normalized)) {
            return ALEX;
        }
        if ("cape".equals(normalized)) {
            return CAPE;
        }
        return STEVE;
    }
}
