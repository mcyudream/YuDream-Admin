package online.yudream.base.plugin.blessing.interfaces.request;

public record SkinSettingsSaveRequest(
        Integer maxPlayersPerUser,
        Boolean allowPublicUpload,
        String siteNotice
) {
}
