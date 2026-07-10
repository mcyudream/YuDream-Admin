package online.yudream.base.domain.platform.satori.model;

import online.yudream.base.domain.platform.satori.enumerate.SatoriChannelType;
import online.yudream.base.domain.platform.satori.enumerate.SatoriLoginStatus;

import java.util.List;
import java.util.Map;

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
            SatoriGuild guild,
            SatoriLogin login,
            SatoriGuildMember member,
            SatoriMessage message,
            SatoriUser operator,
            SatoriGuildRole role,
            SatoriUser user,
            String extensionType,
            Map<String, Object> extensionData
    ) {
        public SatoriEvent {
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

    public record SatoriGuildMember(SatoriUser user, String nick, String avatar, Long joinedAt) {
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

    public record SatoriMeta(String protocolVersion, String adapter, List<String> features) {
        public SatoriMeta {
            features = features == null ? List.of() : List.copyOf(features);
        }
    }

    /** 用于资源夹具和内部测试的聚合视图，不属于 Satori HTTP API。 */
    public record SatoriResourceBundle(SatoriFriend friend, SatoriEmoji emoji, SatoriMeta meta) {
    }
}
