package online.yudream.base.domain.platform.satori.model;

import java.util.List;
import java.util.Map;

/** Strongly typed Satori v1 HTTP API request and transport models. */
public final class SatoriApiModels {
    private SatoriApiModels() {
    }

    public record SatoriApiContext(String baseUrl, String token, String platform, String userId) {
        @Override
        public String toString() {
            return "SatoriApiContext[baseUrl=" + baseUrl + ", token=<redacted>, platform=" + platform + ", userId=" + userId + "]";
        }
    }

    public record ChannelCreate(String guildId, SatoriModels.SatoriChannel data) {
    }
    public record ChannelUpdate(String channelId, SatoriModels.SatoriChannel data) {
    }
    public record ChannelId(String channelId) {
    }
    public record ChannelList(String guildId, String next) {
    }
    public record ChannelMute(String channelId, Long duration) {
    }
    public record UserChannelCreate(String userId, String guildId) {
    }
    public record MessageCreate(String channelId, String content, Map<String, Object> referrer) {
        public MessageCreate { referrer = referrer == null ? Map.of() : Map.copyOf(referrer); }
    }
    public record MessageRef(String channelId, String messageId) {
    }
    public record MessageUpdate(String channelId, String messageId, String content) {
    }
    public record MessageList(String channelId, String next, String direction, Integer limit, String order) {
    }
    public record UserId(String userId) {
    }
    public record GuildId(String guildId) {
    }
    public record Cursor(String next) {
    }
    public record Approve(String messageId, boolean approve, String comment) {
    }
    public record GuildMemberRef(String guildId, String userId) {
    }
    public record GuildMemberKick(String guildId, String userId, Boolean permanent) {
    }
    public record GuildMemberMute(String guildId, String userId, Long duration) {
    }
    public record GuildMemberRole(String guildId, String userId, String roleId) {
    }
    public record GuildRoleCreate(String guildId, SatoriModels.SatoriGuildRole role) {
    }
    public record GuildRoleUpdate(String guildId, String roleId, SatoriModels.SatoriGuildRole role) {
    }
    public record GuildRoleRef(String guildId, String roleId) {
    }
    public record Reaction(String channelId, String messageId, String emojiId, String userId) {
    }
    public record UploadFile(String fieldName, String filename, String contentType, byte[] content) {
        public UploadFile {
            if (fieldName == null || fieldName.isBlank() || filename == null || filename.isBlank() || content == null) {
                throw new IllegalArgumentException("上传文件参数不能为空");
            }
            contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
            content = content.clone();
        }
        @Override public byte[] content() { return content.clone(); }
    }
    public record WebhookCreate(String url, String token) {
    }
    public record WebhookDelete(String url) {
    }
    public record InternalRequest(String method, Object body) {
    }
    public record ProxyResource(String contentType, byte[] content) {
        public ProxyResource { content = content == null ? new byte[0] : content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
}
