package online.yudream.base.infra.platform.milky.service;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.milky.event.MilkyEventPublished;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import online.yudream.base.domain.platform.milky.model.OfficialQqBotEventCatalog;
import online.yudream.base.infra.platform.milky.official.OfficialQqBotEventNormalizer;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 把全部入站 QQ 事件写成系统日志，按事件分类落到独立模块，供系统日志页筛选。
 */
@Component
@Slf4j
public class MilkyEventSystemLogger {
    public static final String LOG_MODULE_MDC = "logModule";
    private static final int CONTENT_LIMIT = 80;

    @EventListener
    public void onEvent(MilkyEventPublished published) {
        if (published == null || published.event() == null) {
            return;
        }
        MilkyModels.Event event = published.event();
        Map<String, Object> data = event.data() == null ? Map.of() : event.data();
        String nativeType = firstText(data, "native_type");
        String scene = firstText(data, "message_scene");
        OfficialQqBotEventCatalog.Kind kind = OfficialQqBotEventCatalog.resolve(nativeType, event.eventType(), scene);
        String previous = MDC.get(LOG_MODULE_MDC);
        MDC.put(LOG_MODULE_MDC, kind.category());
        String content = content(data);
        List<String> atMentions = OfficialQqBotEventNormalizer.mentionIdsFromContent(content);
        try {
            log.info("QQ 事件 [{}] {} connectionId={} scene={} peer={} sender={} seq={} mentionSelf={} self={} at={} content={}",
                    firstNonBlank(kind.nativeType(), nativeType, event.eventType()),
                    kind.label(),
                    published.connectionId(),
                    firstNonBlank(scene, "-"),
                    firstNonBlank(firstText(data, "peer_id", "group_id", "channel_id", "guild_id"), "-"),
                    firstNonBlank(firstText(data, "sender_id", "user_id"), "-"),
                    firstNonBlank(firstText(data, "message_seq", "msg_id", "request_id"), "-"),
                    data.get("mention_self"),
                    firstNonBlank(event.selfId(), "-"),
                    atMentions.isEmpty() ? "-" : atMentions,
                    preview(content));
        } finally {
            if (previous == null) {
                MDC.remove(LOG_MODULE_MDC);
            } else {
                MDC.put(LOG_MODULE_MDC, previous);
            }
        }
    }

    static String content(Map<String, Object> data) {
        String raw = firstText(data, "raw_message", "comment");
        if (raw != null) {
            return raw;
        }
        return textFromSegments(data.get("segments"));
    }

    static String preview(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= CONTENT_LIMIT) {
            return compact;
        }
        return compact.substring(0, CONTENT_LIMIT) + "...";
    }

    @SuppressWarnings("unchecked")
    private static String textFromSegments(Object raw) {
        if (!(raw instanceof List<?> segments) || segments.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (Object segment : segments) {
            if (!(segment instanceof Map<?, ?> map)) {
                continue;
            }
            Object data = map.get("data");
            if (data instanceof Map<?, ?> payload) {
                Object text = payload.get("text");
                if (text != null) {
                    builder.append(text);
                }
            }
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private static String firstText(Map<String, Object> data, String... keys) {
        if (data == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty() && !"null".equals(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
