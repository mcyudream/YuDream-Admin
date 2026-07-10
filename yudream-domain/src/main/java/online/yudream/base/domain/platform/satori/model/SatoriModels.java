package online.yudream.base.domain.platform.satori.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import online.yudream.base.domain.platform.satori.enumerate.SatoriChannelType;
import online.yudream.base.domain.platform.satori.enumerate.SatoriLoginStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Satori v1 协议资源的不可变 JSON 边界模型。
 *
 * <p>平台标识符、游标与 WebSocket 序号始终使用 {@link String}，避免 JavaScript
 * 安全整数范围外的值发生精度损失。调用端应使用 SNAKE_CASE JSON 命名策略。</p>
 */
public final class SatoriModels {

    private SatoriModels() {
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
            Map<String, Object> extensionData
    ) {
        public SatoriEvent {
            referrer = referrer == null ? Map.of() : Map.copyOf(referrer);
            extensionData = extensionData == null ? Map.of() : Map.copyOf(extensionData);
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

    public record SatoriMeta(JsonNode value) {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public SatoriMeta(JsonNode value) {
            this.value = value == null ? JsonNodeFactory.instance.objectNode() : value.deepCopy();
        }

        @JsonValue
        public JsonNode value() {
            return value;
        }

        @JsonIgnore
        public String impl() {
            return text("impl");
        }

        @JsonIgnore
        public String protocolVersion() {
            return text("protocol_version");
        }

        @JsonIgnore
        public String adapter() {
            return text("adapter");
        }

        @JsonIgnore
        public List<String> features() {
            JsonNode featureValues = value.path("features");
            if (!featureValues.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            featureValues.forEach(item -> values.add(item.asText()));
            return List.copyOf(values);
        }

        @JsonIgnore
        public Map<String, JsonNode> extraFields() {
            if (!value.isObject()) {
                return Map.of();
            }
            Map<String, JsonNode> extras = new LinkedHashMap<>();
            value.fields().forEachRemaining(entry -> {
                if (!Set.of("impl", "protocol_version", "adapter", "features").contains(entry.getKey())) {
                    extras.put(entry.getKey(), entry.getValue().deepCopy());
                }
            });
            return Map.copyOf(extras);
        }

        private String text(String name) {
            JsonNode field = value.get(name);
            return field == null || field.isNull() ? null : field.asText();
        }
    }

    /** 用于资源夹具和内部测试的聚合视图，不属于 Satori HTTP API。 */
    public record SatoriResourceBundle(SatoriFriend friend, SatoriEmoji emoji, SatoriMeta meta) {
    }
}
