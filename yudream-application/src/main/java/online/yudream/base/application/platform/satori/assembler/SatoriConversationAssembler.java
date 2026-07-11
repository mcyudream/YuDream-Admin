package online.yudream.base.application.platform.satori.assembler;

import online.yudream.base.application.platform.satori.dto.SatoriChatMemberDTO;
import online.yudream.base.application.platform.satori.dto.SatoriChatMessageDTO;
import online.yudream.base.application.platform.satori.dto.SatoriConversationDTO;
import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriChannel;
import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriGuild;
import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriGuildMember;
import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriFriend;
import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriMessage;
import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriUser;

public final class SatoriConversationAssembler {
    private SatoriConversationAssembler() {
    }

    public static SatoriConversationDTO toConversation(SatoriGuild guild, SatoriChannel channel) {
        return SatoriConversationDTO.builder()
                .channelId(channel.id())
                .guildId(guild.id())
                .name(blank(channel.name()) ? guild.name() : channel.name())
                .type("GROUP")
                .avatar(guild.avatar())
                .build();
    }

    public static SatoriConversationDTO toFriend(SatoriFriend friend) {
        SatoriUser user = friend.user();
        return SatoriConversationDTO.builder()
                .targetUserId(user == null ? null : user.id())
                .name(blank(friend.remark()) ? displayName(user, null) : friend.remark())
                .type("FRIEND")
                .avatar(user == null ? null : user.avatar())
                .build();
    }

    public static SatoriConversationDTO toDirectConversation(String targetUserId, SatoriChannel channel) {
        return SatoriConversationDTO.builder().channelId(channel.id()).targetUserId(targetUserId)
                .name(channel.name()).type("FRIEND").build();
    }

    public static SatoriChatMessageDTO toMessage(SatoriMessage message) {
        return toMessage(message, null);
    }

    public static SatoriChatMessageDTO toMessage(SatoriMessage message, String fallbackChannelId) {
        return toMessage(message, fallbackChannelId, null, null, null);
    }

    public static SatoriChatMessageDTO toMessage(SatoriMessage message, String fallbackChannelId, SatoriUser fallbackUser,
                                                  SatoriGuildMember fallbackMember, Long fallbackCreatedAt) {
        SatoriGuildMember member = message.member() == null ? fallbackMember : message.member();
        SatoriUser user = message.user();
        if (user == null && member != null) user = member.user();
        if (user == null) user = fallbackUser;
        return SatoriChatMessageDTO.builder()
                .id(message.id())
                .channelId(message.channel() == null ? fallbackChannelId : message.channel().id())
                .content(message.content())
                .userId(user == null ? null : user.id())
                .userName(displayName(user, member))
                .userAvatar(member != null && !blank(member.avatar()) ? member.avatar() : (user == null ? null : user.avatar()))
                .createdAt(message.createdAt() == null ? fallbackCreatedAt : message.createdAt())
                .build();
    }

    public static SatoriChatMemberDTO toMember(SatoriGuildMember member) {
        SatoriUser user = member.user();
        return SatoriChatMemberDTO.builder()
                .userId(user == null ? null : user.id())
                .name(blank(member.nick()) ? displayName(user, null) : member.nick())
                .avatar(blank(member.avatar()) ? (user == null ? null : user.avatar()) : member.avatar())
                .build();
    }

    private static String displayName(SatoriUser user, SatoriGuildMember member) {
        if (member != null && !blank(member.nick())) return member.nick();
        if (user == null) return "未知用户";
        if (!blank(user.nick())) return user.nick();
        if (!blank(user.name())) return user.name();
        return user.id();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
