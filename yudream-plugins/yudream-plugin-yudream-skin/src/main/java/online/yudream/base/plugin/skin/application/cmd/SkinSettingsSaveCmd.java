package online.yudream.base.plugin.skin.application.cmd;

public record SkinSettingsSaveCmd(
        Integer maxPlayersPerUser,
        Boolean allowPublicUpload,
        String siteNotice
) {
}
