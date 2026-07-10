package online.yudream.base.domain.platform.satori.message;

import java.util.List;
import java.util.Map;

/** Platform-neutral content submitted to the Satori delivery use case. */
public record SatoriMessageContent(Type type, String content, List<Attachment> attachments,
                                   Map<String, Object> referrer) {
    public SatoriMessageContent {
        type = type == null ? Type.TEXT : type;
        content = content == null ? "" : content;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        referrer = referrer == null ? Map.of() : Map.copyOf(referrer);
    }

    public enum Type { TEXT, SATORI, MARKDOWN, HTML, IMAGE, AUDIO, VIDEO, FILE, COMPOSITE }

    public record Attachment(String url, String title, String contentType) {
        public Attachment {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("Message attachment URL must not be blank");
            }
            url = url.trim();
            title = title == null || title.isBlank() ? null : title.trim();
            contentType = contentType == null || contentType.isBlank() ? null : contentType.trim();
        }
    }
}
