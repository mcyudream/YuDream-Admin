package online.yudream.base.domain.platform.satori.model;

import online.yudream.base.domain.platform.satori.enumerate.SatoriChannelType;
import online.yudream.base.domain.platform.satori.enumerate.SatoriLoginStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Satori v1 协议资源的不可变领域模型。
 * <p>平台标识符、游标与 WebSocket 序号始终使用 {@link String}，避免 JavaScript 安全整数范围外的值发生精度损失。</p>
 */
public final class SatoriModels {

    private SatoriModels() {
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> copy.put(String.valueOf(key), immutableValue(nestedValue)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(immutableValue(item)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    public record SatoriLogin(
            String sn,
            String platform,
            String selfId,
            SatoriUser user,
            SatoriLoginStatus status,
            String adapter,
            List<String> features
    ) {
        public SatoriLogin {
            features = features == null ? List.of() : List.copyOf(features);
        }
    }

    public record SatoriEvent(
            String sn,
            String id,
            String type,
            String platform,
            String selfId,
            Long timestamp,
            SatoriArgv argv,
            SatoriButton button,
            SatoriChannel channel,
            SatoriEmoji emoji,
            SatoriFriend friend,
            SatoriGuild guild,
            SatoriLogin login,
            SatoriGuildMember member,
            SatoriMessage message,
            SatoriUser operator,
            SatoriGuildRole role,
            SatoriUser user,
            Map<String, Object> referrer,
            String extensionType,
            Object extensionData
    ) {
        public SatoriEvent {
            referrer = immutableMap(referrer);
            extensionData = immutableValue(extensionData);
        }
    }

    public record SatoriMessage(
            String id,
            String content,
            SatoriChannel channel,
            SatoriGuild guild,
            SatoriGuildMember member,
            SatoriUser user,
            Long createdAt,
            Long updatedAt
    ) {
    }

    public record SatoriChannel(String id, SatoriChannelType type, String name, String parentId) {
    }

    public record SatoriGuild(String id, String name, String avatar) {
    }

    public record SatoriGuildMember(SatoriUser user, String nick, String avatar, Long joinedAt, List<SatoriGuildRole> roles) {
        public SatoriGuildMember {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    public record SatoriGuildRole(String id, String name, Integer color, Integer position) {
    }

    public record SatoriFriend(SatoriUser user, String nick, String remark) {
    }

    public record SatoriEmoji(String id, String name, String url) {
    }

    public record SatoriUser(String id, String name, String nick, String avatar, Boolean isBot) {
    }

    public record SatoriArgv(String name, List<Object> arguments, Map<String, Object> options) {
        public SatoriArgv {
            arguments = arguments == null ? List.of() : List.copyOf(arguments);
            options = options == null ? Map.of() : Map.copyOf(options);
        }
    }

    public record SatoriButton(String id) {
    }

    public record SatoriMeta(
            String impl,
            String protocolVersion,
            String adapter,
            List<String> features,
            Map<String, Object> extraFields
    ) {
        private static final Set<String> RESERVED_FIELDS = Set.of("impl", "protocol_version", "adapter", "features");

        public SatoriMeta {
            features = features == null ? List.of() : List.copyOf(features);
            extraFields = SatoriModels.immutableMap(withoutReservedFields(extraFields));
        }

        public static SatoriMeta of(String impl, String protocolVersion, String adapter, List<String> features,
                                    Map<String, Object> fields) {
            Map<String, Object> extras = new LinkedHashMap<>();
            if (fields != null) {
                fields.forEach((key, value) -> {
                    if (!RESERVED_FIELDS.contains(key)) {
                        extras.put(key, value);
                    }
                });
            }
            return new SatoriMeta(impl, protocolVersion, adapter, features, extras);
        }

        private static Map<String, Object> withoutReservedFields(Map<String, Object> source) {
            if (source == null || source.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, value) -> {
                if (!RESERVED_FIELDS.contains(key)) {
                    result.put(key, value);
                }
            });
            return result;
        }

    }
}
