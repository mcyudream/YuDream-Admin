package online.yudream.base.plugin.activityproof.domain.aggregate;

public record ActivityProofSettings(
        String id,
        Long templateId,
        String templateCode,
        String templateName,
        String templateFilename,
        long templateUpdatedAt,
        String defaultActivityName,
        String defaultCollege,
        String defaultIssuer,
        long updatedAt
) {
    public static final String ID = "default";

    public static ActivityProofSettings empty() {
        return new ActivityProofSettings(ID, null, "", "", "", 0, "", "", "", 0);
    }

    public ActivityProofSettings withTemplate(Long templateId, String templateCode, String templateName,
                                              String filename, long templateUpdatedAt, long updatedAt) {
        return new ActivityProofSettings(ID, templateId, text(templateCode), text(templateName), text(filename), templateUpdatedAt,
                defaultActivityName, defaultCollege, defaultIssuer, updatedAt);
    }

    public ActivityProofSettings withDefaults(String activityName, String college, String issuer, long updatedAt) {
        return new ActivityProofSettings(ID, templateId, templateCode, templateName, templateFilename, templateUpdatedAt,
                text(activityName), text(college), text(issuer), updatedAt);
    }

    public boolean hasTemplate() {
        return templateId != null;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
