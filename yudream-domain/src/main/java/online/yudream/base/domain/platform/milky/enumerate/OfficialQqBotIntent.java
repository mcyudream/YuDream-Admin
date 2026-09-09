package online.yudream.base.domain.platform.milky.enumerate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 官方 QQ 机器人 Gateway Identify 的 intents 位。
 * 位值对齐开放平台文档，未勾选的 intent 网关不会推送对应事件。
 */
public enum OfficialQqBotIntent {
    GUILDS(1 << 0, "频道", "频道创建/更新/删除、子频道变更", "频道"),
    GUILD_MEMBERS(1 << 1, "频道成员", "频道成员进出与资料变更", "频道"),
    GUILD_MESSAGES(1 << 9, "频道消息（私域）", "文字子频道全量消息，仅私域机器人", "频道"),
    GUILD_MESSAGE_REACTIONS(1 << 10, "频道表情表态", "频道消息表情添加/移除", "频道"),
    DIRECT_MESSAGE(1 << 12, "频道私信", "频道私信创建与删除", "频道"),
    OPEN_FORUMS_EVENT(1 << 18, "论坛（公域）", "公域论坛帖子/回复/评论", "论坛与音频"),
    AUDIO_OR_LIVE_CHANNEL_MEMBER(1 << 19, "音视频/直播成员", "音视频、直播子频道成员进出", "论坛与音频"),
    GROUP_AND_C2C_EVENT(1 << 25, "群与单聊", "群@/群消息、单聊、入退群、好友变更等", "群与单聊"),
    INTERACTION(1 << 26, "互动事件", "按钮、键盘、指令面板点击", "互动"),
    MESSAGE_AUDIT(1 << 27, "消息审核", "频道消息审核通过/不通过", "频道"),
    FORUMS_EVENT(1 << 28, "论坛（私域）", "私域论坛帖子/回复/评论", "论坛与音频"),
    AUDIO_ACTION(1 << 29, "音频动作", "音频开始/结束/上麦/下麦", "论坛与音频"),
    PUBLIC_GUILD_MESSAGES(1 << 30, "频道@消息（公域）", "公域文字子频道 @机器人 消息", "频道");

    private final int bit;
    private final String label;
    private final String description;
    private final String group;

    OfficialQqBotIntent(int bit, String label, String description, String group) {
        this.bit = bit;
        this.label = label;
        this.description = description;
        this.group = group;
    }

    public int bit() {
        return bit;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String group() {
        return group;
    }

    public boolean selected(int mask) {
        return (mask & bit) == bit;
    }

    public static int mask(Collection<OfficialQqBotIntent> selected) {
        int mask = 0;
        if (selected == null) {
            return mask;
        }
        for (OfficialQqBotIntent intent : selected) {
            if (intent != null) {
                mask |= intent.bit;
            }
        }
        return mask;
    }

    public static List<OfficialQqBotIntent> decode(int mask) {
        List<OfficialQqBotIntent> selected = new ArrayList<>();
        for (OfficialQqBotIntent intent : values()) {
            if (intent.selected(mask)) {
                selected.add(intent);
            }
        }
        return List.copyOf(selected);
    }

    public static int recommendedMask() {
        return GROUP_AND_C2C_EVENT.bit | INTERACTION.bit | PUBLIC_GUILD_MESSAGES.bit;
    }

    public static int allMask() {
        int mask = 0;
        for (OfficialQqBotIntent intent : values()) {
            mask |= intent.bit;
        }
        return mask;
    }
}
