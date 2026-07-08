package online.yudream.base.plugin.activityproof.domain.aggregate;

public record ActivityProofSettings(
        String id,
        String templateObjectKey,
        String templateFilename,
        long templateUpdatedAt,
        String defaultActivityName,
        String defaultCollege,
        String defaultIssuer,
        long updatedAt
) {
    public static final String ID = "default";

    public static ActivityProofSettings empty() {
        return new ActivityProofSettings(ID, null, null, 0, "", "", "", 0);
    }

    public ActivityProofSettings withTemplate(String objectKey, String filename, long updatedAt) {
        return new ActivityProofSettings(ID, objectKey, filename, updatedAt,
                defaultActivityName, defaultCollege, defaultIssuer, updatedAt);
    }

    public ActivityProofSettings withDefaults(String activityName, String college, String issuer, long updatedAt) {
        return new ActivityProofSettings(ID, templateObjectKey, templateFilename, templateUpdatedAt,
                text(activityName), text(college), text(issuer), updatedAt);
    }

    public boolean hasTemplate() {
        return templateObjectKey != null && !templateObjectKey.isBlank();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
