package online.yudream.base.plugin.skin.domain.valobj;

public record SkinSiteSettings(
        Integer maxPlayersPerUser,
        Boolean allowPublicUpload,
        String siteNotice
) {
    public static SkinSiteSettings defaults() {
        return new SkinSiteSettings(3, true, "");
    }

    public int safeMaxPlayersPerUser() {
        return maxPlayersPerUser == null ? defaults().maxPlayersPerUser() : maxPlayersPerUser;
    }

    public boolean publicUploadEnabled() {
        return allowPublicUpload == null || allowPublicUpload;
    }
}
