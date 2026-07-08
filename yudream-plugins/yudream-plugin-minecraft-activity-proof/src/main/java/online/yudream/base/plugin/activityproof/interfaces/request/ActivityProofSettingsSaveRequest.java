package online.yudream.base.plugin.activityproof.interfaces.request;

public record ActivityProofSettingsSaveRequest(
        String defaultActivityName,
        String defaultCollege,
        String defaultIssuer
) {
}
