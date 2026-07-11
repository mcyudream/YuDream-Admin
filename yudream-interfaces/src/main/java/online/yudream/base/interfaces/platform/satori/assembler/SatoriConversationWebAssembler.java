package online.yudream.base.interfaces.platform.satori.assembler;

import online.yudream.base.application.platform.satori.dto.SatoriChatMemberDTO;
import online.yudream.base.application.platform.satori.dto.SatoriChatMessageDTO;
import online.yudream.base.application.platform.satori.dto.SatoriChatMessagePageDTO;
import online.yudream.base.application.platform.satori.dto.SatoriConversationDTO;
import online.yudream.base.application.platform.satori.dto.SatoriConversationPageDTO;
import online.yudream.base.interfaces.platform.satori.res.SatoriChatMemberRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriChatMessagePageRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriChatMessageRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriConversationPageRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriConversationRes;

import java.util.List;

public final class SatoriConversationWebAssembler {
    private SatoriConversationWebAssembler() {
    }

    public static SatoriConversationPageRes toRes(SatoriConversationPageDTO dto) {
        return SatoriConversationPageRes.builder().records(dto.getRecords().stream().map(SatoriConversationWebAssembler::toRes).toList())
                .next(dto.getNext()).build();
    }

    public static SatoriChatMessagePageRes toRes(SatoriChatMessagePageDTO dto) {
        return SatoriChatMessagePageRes.builder().records(dto.getRecords().stream().map(SatoriConversationWebAssembler::toRes).toList())
                .prev(dto.getPrev()).next(dto.getNext()).build();
    }

    public static List<SatoriChatMemberRes> toMemberRes(List<SatoriChatMemberDTO> members) {
        return members.stream().map(SatoriConversationWebAssembler::toRes).toList();
    }

    public static SatoriConversationRes toRes(SatoriConversationDTO dto) {
        return SatoriConversationRes.builder().channelId(dto.getChannelId()).guildId(dto.getGuildId()).targetUserId(dto.getTargetUserId())
                .name(dto.getName()).type(dto.getType()).avatar(dto.getAvatar()).build();
    }

    public static SatoriChatMessageRes toRes(SatoriChatMessageDTO dto) {
        return SatoriChatMessageRes.builder().id(dto.getId()).channelId(dto.getChannelId()).content(dto.getContent()).userId(dto.getUserId())
                .userName(dto.getUserName()).userAvatar(dto.getUserAvatar()).createdAt(dto.getCreatedAt()).build();
    }

    private static SatoriChatMemberRes toRes(SatoriChatMemberDTO dto) {
        return SatoriChatMemberRes.builder().userId(dto.getUserId()).name(dto.getName()).avatar(dto.getAvatar()).build();
    }
}
