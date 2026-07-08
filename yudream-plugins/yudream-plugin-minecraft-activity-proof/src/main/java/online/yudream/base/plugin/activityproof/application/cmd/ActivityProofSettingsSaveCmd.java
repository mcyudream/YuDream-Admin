package online.yudream.base.plugin.activityproof.application.cmd;

public record ActivityProofSettingsSaveCmd(
        String defaultActivityName,
        String defaultCollege,
        String defaultIssuer,
        String templateId
) {
}
