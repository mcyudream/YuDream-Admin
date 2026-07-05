package online.yudream.base.plugin.blessing.application.cmd;

public record SkinSettingsSaveCmd(
        Integer maxPlayersPerUser,
        Boolean allowPublicUpload,
        String siteNotice
) {
}
