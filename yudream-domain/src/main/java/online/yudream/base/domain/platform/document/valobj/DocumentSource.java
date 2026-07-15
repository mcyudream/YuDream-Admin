package online.yudream.base.domain.platform.document.valobj;

public record DocumentSource(
        String content,
        String contentType,
        String fileName
) {

    public static DocumentSource dataUrl(String dataUrl) {
        return new DocumentSource(dataUrl, null, null);
    }

    public static DocumentSource base64(String content, String contentType, String fileName) {
        return new DocumentSource(content, contentType, fileName);
    }
}
