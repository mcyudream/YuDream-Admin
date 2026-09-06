package online.yudream.base.domain.platform.milky.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 消息连接线模型。QQ 标识在边界上保持字符串。 */
public final class MilkyModels {
    private MilkyModels() { }

    public record Context(String protocol, String baseUrl, String token, String appId, String appSecret,
                          boolean sandbox, String selfId, Long connectionId) {
        public Context(String baseUrl, String token, String selfId) {
            this("milky", baseUrl, token, null, null, false, selfId, null);
        }

        public boolean official() {
            return protocol != null && "official".equalsIgnoreCase(protocol);
        }
    }

    public record Response<T>(String status, Integer retcode, T data, String message) {
        public boolean successful() { return retcode == null || retcode == 0; }
    }
    public record Segment(String type, Map<String, Object> data) {
        public Segment { data = copyWithoutNulls(data); }
    }
    /** Documented outgoing message segment variants. IDs remain strings to preserve QQ precision. */
    public record TextSegment(String text) { }
    public record MentionSegment(String userId) { }
    public record FaceSegment(String faceId, Boolean isLarge) { }
    public record ReplySegment(String messageSeq) { }
    public record ImageSegment(String file, String summary, String subType) { }
    public record RecordSegment(String file, Integer duration) { }
    public record VideoSegment(String file, String thumb, Integer duration) { }
    public record FileSegment(String file, String name, Long size) { }
    public record ForwardSegment(String resId, List<ForwardNode> nodes) {
        public ForwardSegment { nodes = nodes == null ? List.of() : List.copyOf(nodes); }
    }
    public record ForwardNode(String userId, String nickname, Long time, List<Segment> segments) {
        public ForwardNode { segments = segments == null ? List.of() : List.copyOf(segments); }
    }
    public record MiniAppSegment(String appId, String title, String content, String url, String icon) { }
    public record ImageInfo(String fileId, String url, Integer width, Integer height, Long size) { }
    public record FileInfo(String fileId, String name, Long size, String url, String path) { }
    public record Event(Long time, String selfId, String eventType, Map<String, Object> data) {
        public Event { data = copyWithoutNulls(data); }
    }
    public record Friend(String userId, String nickname, String remark, String avatar) { }
    public record Group(String groupId, String groupName, String avatar) { }
    public record GroupMember(String userId, String nickname, String card, String avatar, String role) { }
    public record GroupAnnouncement(String announcementId, String senderId, String content, Long publishTime) { }
    public record GroupFile(String fileId, String name, Long size, String parentFolderId, String uploaderId) { }
    public record GroupFolder(String folderId, String name, String parentFolderId, Long createTime) { }
    public record FriendRequest(String requestId, String userId, String nickname, String comment) { }
    public record GroupNotification(String notificationId, String groupId, String userId, String type, String comment) { }
    public record IncomingMessage(String messageScene, String peerId, String senderId, String messageSeq,
                                  Long time, List<Segment> segments) {
        public IncomingMessage { segments = segments == null ? List.of() : List.copyOf(segments); }
    }

    static Map<String, Object> copyWithoutNulls(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        data.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, value);
            }
        });
        return copy.isEmpty() ? Map.of() : Map.copyOf(copy);
    }
}
