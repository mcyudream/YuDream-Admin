package online.yudream.base.plugin.skin.interfaces.request;

public record SkinSettingsSaveRequest(
        Integer maxPlayersPerUser,
        Boolean allowPublicUpload,
        String siteNotice
) {
}
