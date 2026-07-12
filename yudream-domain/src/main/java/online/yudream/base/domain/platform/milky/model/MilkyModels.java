package online.yudream.base.domain.platform.milky.model;

import java.util.List;
import java.util.Map;

/** Milky v1 wire models. QQ identifiers intentionally remain strings at boundaries. */
public final class MilkyModels {
    private MilkyModels() { }

    public record Context(String baseUrl, String token, String selfId) { }
    public record Response<T>(String status, Integer retcode, T data, String message) {
        public boolean successful() { return retcode == null || retcode == 0; }
    }
    public record Segment(String type, Map<String, Object> data) {
        public Segment { data = data == null ? Map.of() : Map.copyOf(data); }
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
        public Event { data = data == null ? Map.of() : Map.copyOf(data); }
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
}
