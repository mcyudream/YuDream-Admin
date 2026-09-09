package online.yudream.base.domain.platform.milky.model;

import online.yudream.base.domain.platform.milky.enumerate.OfficialQqBotIntent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 官方 QQ 机器人事件分类目录：native_type / 内部 eventType 映射到中文标签与系统日志模块。
 */
public final class OfficialQqBotEventCatalog {
    public static final String CATEGORY_GROUP_MESSAGE = "QQ 群消息";
    public static final String CATEGORY_PRIVATE_MESSAGE = "QQ 单聊";
    public static final String CATEGORY_INTERACTION = "QQ 互动";
    public static final String CATEGORY_GROUP_ADMIN = "QQ 群管理";
    public static final String CATEGORY_FRIEND = "QQ 好友";
    public static final String CATEGORY_GUILD = "QQ 频道";
    public static final String CATEGORY_FORUM = "QQ 论坛";
    public static final String CATEGORY_AUDIO = "QQ 音频";
    public static final String CATEGORY_GATEWAY = "QQ 网关";
    public static final String CATEGORY_OTHER = "QQ 其他";
    public static final String CATEGORY_PLATFORM = "QQ 消息平台";

    public static final List<String> LOG_MODULES = List.of(
            CATEGORY_GROUP_MESSAGE,
            CATEGORY_PRIVATE_MESSAGE,
            CATEGORY_INTERACTION,
            CATEGORY_GROUP_ADMIN,
            CATEGORY_FRIEND,
            CATEGORY_GUILD,
            CATEGORY_FORUM,
            CATEGORY_AUDIO,
            CATEGORY_GATEWAY,
            CATEGORY_OTHER,
            CATEGORY_PLATFORM
    );

    private static final Map<String, Kind> BY_NATIVE = kinds();

    private OfficialQqBotEventCatalog() {
    }

    public record Kind(String nativeType, String label, String category, OfficialQqBotIntent intent) {
        public Kind {
            nativeType = nativeType == null ? "" : nativeType;
            label = label == null || label.isBlank() ? nativeType : label;
            category = category == null || category.isBlank() ? CATEGORY_OTHER : category;
        }
    }

    public static Kind resolve(String nativeType, String eventType, String scene) {
        Kind byNative = lookup(nativeType);
        if (byNative != null) {
            return byNative;
        }
        Kind byEvent = lookup(eventType);
        if (byEvent != null) {
            if (genericEventType(eventType) && scene != null && !scene.isBlank()) {
                String inferred = categoryByScene(scene, eventType);
                if (!inferred.equals(byEvent.category())) {
                    return new Kind(byEvent.nativeType(), byEvent.label(), inferred, byEvent.intent());
                }
            }
            return byEvent;
        }
        String inferredNative = firstNonBlank(nativeType, eventType, "UNKNOWN");
        String inferredLabel = firstNonBlank(eventType, nativeType, "未知事件");
        return new Kind(inferredNative, inferredLabel, categoryByScene(scene, eventType), null);
    }

    public static List<Kind> all() {
        return List.copyOf(BY_NATIVE.values());
    }

    private static boolean genericEventType(String eventType) {
        return eventType != null && "message_receive".equalsIgnoreCase(eventType);
    }

    private static Kind lookup(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        Kind exact = BY_NATIVE.get(type);
        if (exact != null) {
            return exact;
        }
        return BY_NATIVE.get(type.toUpperCase(Locale.ROOT));
    }

    private static String categoryByScene(String scene, String eventType) {
        String normalizedScene = scene == null ? "" : scene.toLowerCase(Locale.ROOT);
        String normalizedEvent = eventType == null ? "" : eventType.toLowerCase(Locale.ROOT);
        if (normalizedEvent.contains("ready") || normalizedEvent.contains("gateway")) {
            return CATEGORY_GATEWAY;
        }
        if (normalizedEvent.contains("button") || normalizedEvent.contains("interaction")) {
            return CATEGORY_INTERACTION;
        }
        if (normalizedEvent.contains("friend")) {
            return CATEGORY_FRIEND;
        }
        if (normalizedEvent.contains("group_request")
                || normalizedEvent.contains("group_member")
                || normalizedEvent.contains("group_join")
                || normalizedEvent.contains("group_leave")) {
            return CATEGORY_GROUP_ADMIN;
        }
        if (normalizedEvent.contains("guild") || normalizedEvent.contains("channel") || "channel".equals(normalizedScene) || "dm".equals(normalizedScene)) {
            return CATEGORY_GUILD;
        }
        if ("friend".equals(normalizedScene) || "c2c".equals(normalizedScene) || normalizedEvent.contains("private")) {
            return CATEGORY_PRIVATE_MESSAGE;
        }
        if ("group".equals(normalizedScene) || normalizedEvent.contains("message")) {
            return CATEGORY_GROUP_MESSAGE;
        }
        return CATEGORY_OTHER;
    }

    private static Map<String, Kind> kinds() {
        Map<String, Kind> kinds = new LinkedHashMap<>();
        put(kinds, "GROUP_AT_MESSAGE_CREATE", "群@消息", CATEGORY_GROUP_MESSAGE, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "GROUP_MESSAGE_CREATE", "群普通消息", CATEGORY_GROUP_MESSAGE, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "C2C_MESSAGE_CREATE", "单聊消息", CATEGORY_PRIVATE_MESSAGE, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "GROUP_ADD_ROBOT", "机器人入群", CATEGORY_GROUP_ADMIN, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "GROUP_DEL_ROBOT", "机器人退群", CATEGORY_GROUP_ADMIN, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "GROUP_MSG_REJECT", "群拒绝机器人消息", CATEGORY_GROUP_ADMIN, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "GROUP_MSG_RECEIVE", "群恢复机器人消息", CATEGORY_GROUP_ADMIN, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "C2C_MSG_REJECT", "单聊拒绝机器人消息", CATEGORY_PRIVATE_MESSAGE, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "C2C_MSG_RECEIVE", "单聊恢复机器人消息", CATEGORY_PRIVATE_MESSAGE, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "FRIEND_ADD", "添加好友", CATEGORY_FRIEND, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "FRIEND_DEL", "删除好友", CATEGORY_FRIEND, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "GROUP_JOIN_REQUEST", "入群申请", CATEGORY_GROUP_ADMIN, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "GROUP_MEMBER_ADD", "成员入群", CATEGORY_GROUP_ADMIN, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "GROUP_MEMBER_REMOVE", "成员退群", CATEGORY_GROUP_ADMIN, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "SUBSCRIBE_MESSAGE_STATUS", "订阅消息状态", CATEGORY_OTHER, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "INTERACTION_CREATE", "互动事件", CATEGORY_INTERACTION, OfficialQqBotIntent.INTERACTION);
        put(kinds, "AT_MESSAGE_CREATE", "频道@消息", CATEGORY_GUILD, OfficialQqBotIntent.PUBLIC_GUILD_MESSAGES);
        put(kinds, "PUBLIC_MESSAGE_DELETE", "公域频道消息删除", CATEGORY_GUILD, OfficialQqBotIntent.PUBLIC_GUILD_MESSAGES);
        put(kinds, "MESSAGE_CREATE", "频道消息（私域）", CATEGORY_GUILD, OfficialQqBotIntent.GUILD_MESSAGES);
        put(kinds, "MESSAGE_DELETE", "频道消息删除", CATEGORY_GUILD, OfficialQqBotIntent.GUILD_MESSAGES);
        put(kinds, "DIRECT_MESSAGE_CREATE", "频道私信", CATEGORY_GUILD, OfficialQqBotIntent.DIRECT_MESSAGE);
        put(kinds, "DIRECT_MESSAGE_DELETE", "频道私信删除", CATEGORY_GUILD, OfficialQqBotIntent.DIRECT_MESSAGE);
        put(kinds, "GUILD_CREATE", "加入频道", CATEGORY_GUILD, OfficialQqBotIntent.GUILDS);
        put(kinds, "GUILD_UPDATE", "频道资料更新", CATEGORY_GUILD, OfficialQqBotIntent.GUILDS);
        put(kinds, "GUILD_DELETE", "退出频道", CATEGORY_GUILD, OfficialQqBotIntent.GUILDS);
        put(kinds, "CHANNEL_CREATE", "子频道创建", CATEGORY_GUILD, OfficialQqBotIntent.GUILDS);
        put(kinds, "CHANNEL_UPDATE", "子频道更新", CATEGORY_GUILD, OfficialQqBotIntent.GUILDS);
        put(kinds, "CHANNEL_DELETE", "子频道删除", CATEGORY_GUILD, OfficialQqBotIntent.GUILDS);
        put(kinds, "GUILD_MEMBER_ADD", "频道成员加入", CATEGORY_GUILD, OfficialQqBotIntent.GUILD_MEMBERS);
        put(kinds, "GUILD_MEMBER_UPDATE", "频道成员更新", CATEGORY_GUILD, OfficialQqBotIntent.GUILD_MEMBERS);
        put(kinds, "GUILD_MEMBER_REMOVE", "频道成员退出", CATEGORY_GUILD, OfficialQqBotIntent.GUILD_MEMBERS);
        put(kinds, "MESSAGE_REACTION_ADD", "添加表情表态", CATEGORY_GUILD, OfficialQqBotIntent.GUILD_MESSAGE_REACTIONS);
        put(kinds, "MESSAGE_REACTION_REMOVE", "移除表情表态", CATEGORY_GUILD, OfficialQqBotIntent.GUILD_MESSAGE_REACTIONS);
        put(kinds, "MESSAGE_AUDIT_PASS", "消息审核通过", CATEGORY_GUILD, OfficialQqBotIntent.MESSAGE_AUDIT);
        put(kinds, "MESSAGE_AUDIT_REJECT", "消息审核不通过", CATEGORY_GUILD, OfficialQqBotIntent.MESSAGE_AUDIT);
        put(kinds, "FORUM_THREAD_CREATE", "论坛主题创建", CATEGORY_FORUM, OfficialQqBotIntent.FORUMS_EVENT);
        put(kinds, "FORUM_THREAD_UPDATE", "论坛主题更新", CATEGORY_FORUM, OfficialQqBotIntent.FORUMS_EVENT);
        put(kinds, "FORUM_THREAD_DELETE", "论坛主题删除", CATEGORY_FORUM, OfficialQqBotIntent.FORUMS_EVENT);
        put(kinds, "FORUM_POST_CREATE", "论坛帖子创建", CATEGORY_FORUM, OfficialQqBotIntent.FORUMS_EVENT);
        put(kinds, "FORUM_POST_DELETE", "论坛帖子删除", CATEGORY_FORUM, OfficialQqBotIntent.FORUMS_EVENT);
        put(kinds, "FORUM_REPLY_CREATE", "论坛回复创建", CATEGORY_FORUM, OfficialQqBotIntent.FORUMS_EVENT);
        put(kinds, "FORUM_REPLY_DELETE", "论坛回复删除", CATEGORY_FORUM, OfficialQqBotIntent.FORUMS_EVENT);
        put(kinds, "OPEN_FORUM_THREAD_CREATE", "公域论坛主题创建", CATEGORY_FORUM, OfficialQqBotIntent.OPEN_FORUMS_EVENT);
        put(kinds, "OPEN_FORUM_THREAD_UPDATE", "公域论坛主题更新", CATEGORY_FORUM, OfficialQqBotIntent.OPEN_FORUMS_EVENT);
        put(kinds, "OPEN_FORUM_THREAD_DELETE", "公域论坛主题删除", CATEGORY_FORUM, OfficialQqBotIntent.OPEN_FORUMS_EVENT);
        put(kinds, "OPEN_FORUM_POST_CREATE", "公域论坛帖子创建", CATEGORY_FORUM, OfficialQqBotIntent.OPEN_FORUMS_EVENT);
        put(kinds, "OPEN_FORUM_POST_DELETE", "公域论坛帖子删除", CATEGORY_FORUM, OfficialQqBotIntent.OPEN_FORUMS_EVENT);
        put(kinds, "OPEN_FORUM_REPLY_CREATE", "公域论坛回复创建", CATEGORY_FORUM, OfficialQqBotIntent.OPEN_FORUMS_EVENT);
        put(kinds, "OPEN_FORUM_REPLY_DELETE", "公域论坛回复删除", CATEGORY_FORUM, OfficialQqBotIntent.OPEN_FORUMS_EVENT);
        put(kinds, "AUDIO_START", "音频开始", CATEGORY_AUDIO, OfficialQqBotIntent.AUDIO_ACTION);
        put(kinds, "AUDIO_FINISH", "音频结束", CATEGORY_AUDIO, OfficialQqBotIntent.AUDIO_ACTION);
        put(kinds, "AUDIO_ON_MIC", "上麦", CATEGORY_AUDIO, OfficialQqBotIntent.AUDIO_ACTION);
        put(kinds, "AUDIO_OFF_MIC", "下麦", CATEGORY_AUDIO, OfficialQqBotIntent.AUDIO_ACTION);
        put(kinds, "AUDIO_OR_LIVE_CHANNEL_MEMBER_ENTER", "音视频成员进入", CATEGORY_AUDIO, OfficialQqBotIntent.AUDIO_OR_LIVE_CHANNEL_MEMBER);
        put(kinds, "AUDIO_OR_LIVE_CHANNEL_MEMBER_EXIT", "音视频成员退出", CATEGORY_AUDIO, OfficialQqBotIntent.AUDIO_OR_LIVE_CHANNEL_MEMBER);
        put(kinds, "READY", "网关就绪", CATEGORY_GATEWAY, null);
        put(kinds, "RESUMED", "网关恢复", CATEGORY_GATEWAY, null);
        put(kinds, "message_receive", "收到消息", CATEGORY_GROUP_MESSAGE, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "button_click", "按钮点击", CATEGORY_INTERACTION, OfficialQqBotIntent.INTERACTION);
        put(kinds, "group_request", "入群申请", CATEGORY_GROUP_ADMIN, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "group_member_increase", "成员入群", CATEGORY_GROUP_ADMIN, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "group_member_decrease", "成员退群", CATEGORY_GROUP_ADMIN, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "friend_add", "添加好友", CATEGORY_FRIEND, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "friend_del", "删除好友", CATEGORY_FRIEND, OfficialQqBotIntent.GROUP_AND_C2C_EVENT);
        put(kinds, "ready", "网关就绪", CATEGORY_GATEWAY, null);
        return Map.copyOf(kinds);
    }

    private static void put(Map<String, Kind> kinds, String nativeType, String label, String category, OfficialQqBotIntent intent) {
        kinds.put(nativeType, new Kind(nativeType, label, category, intent));
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
