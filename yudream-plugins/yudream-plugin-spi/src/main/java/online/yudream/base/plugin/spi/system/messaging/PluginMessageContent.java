package online.yudream.base.plugin.spi.system.messaging;

import java.util.List;
import java.util.Map;

public record PluginMessageContent(
        Type type,
        String content,
        List<Attachment> attachments,
        Map<String, Object> referrer
) {
    public enum Type { TEXT, MARKDOWN, HTML, IMAGE, AUDIO, VIDEO, FILE, COMPOSITE }

    public PluginMessageContent {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        referrer = referrer == null ? Map.of() : Map.copyOf(referrer);
    }

    public record Attachment(String url, String title, String contentType) {
    }
}
