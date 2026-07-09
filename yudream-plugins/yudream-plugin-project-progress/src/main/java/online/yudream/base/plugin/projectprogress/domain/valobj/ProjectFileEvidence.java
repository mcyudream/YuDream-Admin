package online.yudream.base.plugin.projectprogress.domain.valobj;

public record ProjectFileEvidence(
        String objectKey,
        String filename,
        String contentType,
        long size,
        boolean image
) {

    public ProjectFileEvidence {
        objectKey = requireText(objectKey, "文件对象不能为空");
        filename = filename == null || filename.isBlank() ? objectKey : filename.trim();
        contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType.trim();
        size = Math.max(size, 0);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
