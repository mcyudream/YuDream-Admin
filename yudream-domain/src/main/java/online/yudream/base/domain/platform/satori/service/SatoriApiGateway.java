package online.yudream.base.domain.platform.satori.service;

import online.yudream.base.domain.platform.satori.model.SatoriApiModels.*;
import online.yudream.base.domain.platform.satori.model.SatoriBidiPage;
import online.yudream.base.domain.platform.satori.model.SatoriModels.*;
import online.yudream.base.domain.platform.satori.model.SatoriPage;

import java.util.List;
import java.util.Map;

/** Typed transport port for every standard Satori v1 HTTP method. */
public interface SatoriApiGateway {
    SatoriChannel channelGet(SatoriApiContext context, ChannelId request);
    SatoriPage<SatoriChannel> channelList(SatoriApiContext context, ChannelList request);
    SatoriChannel channelCreate(SatoriApiContext context, ChannelCreate request);
    void channelUpdate(SatoriApiContext context, ChannelUpdate request);
    void channelDelete(SatoriApiContext context, ChannelId request);
    void channelMute(SatoriApiContext context, ChannelMute request);
    SatoriChannel userChannelCreate(SatoriApiContext context, UserChannelCreate request);
    List<SatoriMessage> messageCreate(SatoriApiContext context, MessageCreate request);
    SatoriMessage messageGet(SatoriApiContext context, MessageRef request);
    void messageDelete(SatoriApiContext context, MessageRef request);
    void messageUpdate(SatoriApiContext context, MessageUpdate request);
    SatoriBidiPage<SatoriMessage> messageList(SatoriApiContext context, MessageList request);
    SatoriUser userGet(SatoriApiContext context, UserId request);
    SatoriGuild guildGet(SatoriApiContext context, GuildId request);
    SatoriPage<SatoriGuild> guildList(SatoriApiContext context, Cursor request);
    void guildApprove(SatoriApiContext context, Approve request);
    SatoriGuildMember guildMemberGet(SatoriApiContext context, GuildMemberRef request);
    SatoriPage<SatoriGuildMember> guildMemberList(SatoriApiContext context, GuildMemberList request);
    void guildMemberKick(SatoriApiContext context, GuildMemberKick request);
    void guildMemberMute(SatoriApiContext context, GuildMemberMute request);
    void guildMemberApprove(SatoriApiContext context, Approve request);
    void guildMemberRoleSet(SatoriApiContext context, GuildMemberRole request);
    void guildMemberRoleUnset(SatoriApiContext context, GuildMemberRole request);
    SatoriPage<SatoriGuildRole> guildRoleList(SatoriApiContext context, GuildId request);
    SatoriGuildRole guildRoleCreate(SatoriApiContext context, GuildRoleCreate request);
    void guildRoleUpdate(SatoriApiContext context, GuildRoleUpdate request);
    void guildRoleDelete(SatoriApiContext context, GuildRoleRef request);
    SatoriPage<SatoriFriend> friendList(SatoriApiContext context, Cursor request);
    void friendDelete(SatoriApiContext context, UserId request);
    void friendApprove(SatoriApiContext context, Approve request);
    void reactionCreate(SatoriApiContext context, Reaction request);
    void reactionDelete(SatoriApiContext context, Reaction request);
    void reactionClear(SatoriApiContext context, Reaction request);
    SatoriPage<SatoriUser> reactionList(SatoriApiContext context, Reaction request);
    SatoriLogin loginGet(SatoriApiContext context);
    Map<String, String> uploadCreate(SatoriApiContext context, List<UploadFile> files);
    SatoriMeta meta(SatoriApiContext context);
    void webhookCreate(SatoriApiContext context, WebhookCreate request);
    void webhookDelete(SatoriApiContext context, WebhookDelete request);
    ProxyResource proxy(SatoriApiContext context, String url);
}
