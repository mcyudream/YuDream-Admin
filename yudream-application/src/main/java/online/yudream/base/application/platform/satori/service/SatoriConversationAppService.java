package online.yudream.base.application.platform.satori.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.satori.assembler.SatoriConversationAssembler;
import online.yudream.base.application.platform.satori.dto.SatoriChatMemberDTO;
import online.yudream.base.application.platform.satori.dto.SatoriChatMessagePageDTO;
import online.yudream.base.application.platform.satori.dto.SatoriConversationDTO;
import online.yudream.base.application.platform.satori.dto.SatoriConversationPageDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.ChannelList;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.Cursor;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.GuildMemberList;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.MessageList;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.SatoriApiContext;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.UserChannelCreate;
import online.yudream.base.domain.platform.satori.repo.SatoriConnectionRepo;
import online.yudream.base.domain.platform.satori.service.SatoriApiGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Read-side use cases for the Satori operator conversation workspace. */
@Service
@RequiredArgsConstructor
public class SatoriConversationAppService {
    private final CapabilityAppService capabilityAppService;
    private final SatoriConnectionRepo connectionRepo;
    private final SatoriApiGateway apiGateway;

    @Transactional(readOnly = true)
    public SatoriConversationPageDTO conversations(Long connectionId, String next) {
        SatoriApiContext context = contextOf(connectionId);
        var guildPage = apiGateway.guildList(context, new Cursor(blankToNull(next)));
        List<SatoriConversationDTO> records = new ArrayList<>();
        guildPage.data().forEach(guild -> apiGateway.channelList(context, new ChannelList(guild.id(), null)).data()
                .forEach(channel -> records.add(SatoriConversationAssembler.toConversation(guild, channel))));
        if (blank(next)) {
            apiGateway.friendList(context, new Cursor(null)).data().stream()
                    .map(SatoriConversationAssembler::toFriend).forEach(records::add);
        }
        return SatoriConversationPageDTO.builder().records(records).next(guildPage.next()).build();
    }

    @Transactional(readOnly = true)
    public void ensureAvailable(Long connectionId) {
        contextOf(connectionId);
    }

    @Transactional(readOnly = true)
    public SatoriConversationDTO openDirectConversation(Long connectionId, String targetUserId) {
        if (blank(targetUserId)) throw new BizException("好友账号不能为空");
        return SatoriConversationAssembler.toDirectConversation(targetUserId,
                apiGateway.userChannelCreate(contextOf(connectionId), new UserChannelCreate(targetUserId, null)));
    }

    @Transactional(readOnly = true)
    public SatoriChatMessagePageDTO messages(Long connectionId, String channelId, String next, int limit) {
        SatoriApiContext context = contextOf(connectionId);
        if (blank(channelId)) throw new BizException("会话频道不能为空");
        var page = apiGateway.messageList(context, new MessageList(channelId, blankToNull(next), "before", clamp(limit), "asc"));
        return SatoriChatMessagePageDTO.builder().records(page.data().stream().map(SatoriConversationAssembler::toMessage).toList())
                .prev(page.prev()).next(page.next()).build();
    }

    @Transactional(readOnly = true)
    public List<SatoriChatMemberDTO> members(Long connectionId, String guildId, String next) {
        SatoriApiContext context = contextOf(connectionId);
        if (blank(guildId)) return List.of();
        return apiGateway.guildMemberList(context, new GuildMemberList(guildId, blankToNull(next))).data().stream()
                .map(SatoriConversationAssembler::toMember).toList();
    }

    private SatoriApiContext contextOf(Long connectionId) {
        capabilityAppService.ensureEnabled(SatoriConnectionAppService.CAPABILITY_CODE, "Satori 平台");
        if (connectionId == null) throw new BizException("Satori 连接 ID 不能为空");
        SatoriConnection connection = connectionRepo.findById(connectionId)
                .orElseThrow(() -> new BizException("Satori 连接不存在"));
        if (!connection.enabled()) throw new BizException("Satori 连接未启用");
        return new SatoriApiContext(connection.getBaseUrl(), connection.getToken(), connection.getPlatform(), connection.getUserId());
    }

    private int clamp(int limit) {
        return Math.min(Math.max(limit, 1), 100);
    }

    private String blankToNull(String value) {
        return blank(value) ? null : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
