package online.yudream.base.infra.platform.satori.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.*;
import online.yudream.base.domain.platform.satori.model.SatoriBidiPage;
import online.yudream.base.domain.platform.satori.model.SatoriModels.*;
import online.yudream.base.domain.platform.satori.model.SatoriPage;
import online.yudream.base.domain.platform.satori.service.SatoriApiGateway;
import online.yudream.base.domain.platform.satori.service.SatoriInternalGateway;
import online.yudream.base.infra.platform.satori.json.SatoriJsonMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.multipart.MultipartHttpMessageWriter;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Reactor Netty adapter for Satori's JSON and multipart HTTP transport. */
@Service
public class ReactorSatoriApiGateway implements SatoriApiGateway, SatoriInternalGateway {
    private static final Pattern INTERNAL_METHOD = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,127}");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private final ObjectMapper mapper;

    public ReactorSatoriApiGateway() {
        this(SatoriJsonMapper.createObjectMapper());
    }

    ReactorSatoriApiGateway(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override public SatoriChannel channelGet(SatoriApiContext c, ChannelId r) { return post(c, "channel.get", r, SatoriChannel.class); }
    @Override public SatoriPage<SatoriChannel> channelList(SatoriApiContext c, ChannelList r) { return page(c, "channel.list", r, SatoriChannel.class); }
    @Override public SatoriChannel channelCreate(SatoriApiContext c, ChannelCreate r) { return post(c, "channel.create", r, SatoriChannel.class); }
    @Override public void channelUpdate(SatoriApiContext c, ChannelUpdate r) { postVoid(c, "channel.update", r); }
    @Override public void channelDelete(SatoriApiContext c, ChannelId r) { postVoid(c, "channel.delete", r); }
    @Override public void channelMute(SatoriApiContext c, ChannelMute r) { postVoid(c, "channel.mute", r); }
    @Override public SatoriChannel userChannelCreate(SatoriApiContext c, UserChannelCreate r) { return post(c, "user.channel.create", r, SatoriChannel.class); }
    @Override public List<SatoriMessage> messageCreate(SatoriApiContext c, MessageCreate r) { return list(c, "message.create", r, SatoriMessage.class); }
    @Override public SatoriMessage messageGet(SatoriApiContext c, MessageRef r) { return post(c, "message.get", r, SatoriMessage.class); }
    @Override public void messageDelete(SatoriApiContext c, MessageRef r) { postVoid(c, "message.delete", r); }
    @Override public void messageUpdate(SatoriApiContext c, MessageUpdate r) { postVoid(c, "message.update", r); }
    @Override public SatoriBidiPage<SatoriMessage> messageList(SatoriApiContext c, MessageList r) { return bidiPage(c, "message.list", r, SatoriMessage.class); }
    @Override public SatoriUser userGet(SatoriApiContext c, UserId r) { return post(c, "user.get", r, SatoriUser.class); }
    @Override public SatoriGuild guildGet(SatoriApiContext c, GuildId r) { return post(c, "guild.get", r, SatoriGuild.class); }
    @Override public SatoriPage<SatoriGuild> guildList(SatoriApiContext c, Cursor r) { return page(c, "guild.list", r, SatoriGuild.class); }
    @Override public void guildApprove(SatoriApiContext c, Approve r) { postVoid(c, "guild.approve", r); }
    @Override public SatoriGuildMember guildMemberGet(SatoriApiContext c, GuildMemberRef r) { return post(c, "guild.member.get", r, SatoriGuildMember.class); }
    @Override public SatoriPage<SatoriGuildMember> guildMemberList(SatoriApiContext c, GuildMemberRef r) { return page(c, "guild.member.list", r, SatoriGuildMember.class); }
    @Override public void guildMemberKick(SatoriApiContext c, GuildMemberKick r) { postVoid(c, "guild.member.kick", r); }
    @Override public void guildMemberMute(SatoriApiContext c, GuildMemberMute r) { postVoid(c, "guild.member.mute", r); }
    @Override public void guildMemberApprove(SatoriApiContext c, Approve r) { postVoid(c, "guild.member.approve", r); }
    @Override public void guildMemberRoleSet(SatoriApiContext c, GuildMemberRole r) { postVoid(c, "guild.member.role.set", r); }
    @Override public void guildMemberRoleUnset(SatoriApiContext c, GuildMemberRole r) { postVoid(c, "guild.member.role.unset", r); }
    @Override public SatoriPage<SatoriGuildRole> guildRoleList(SatoriApiContext c, GuildId r) { return page(c, "guild.role.list", r, SatoriGuildRole.class); }
    @Override public SatoriGuildRole guildRoleCreate(SatoriApiContext c, GuildRoleCreate r) { return post(c, "guild.role.create", r, SatoriGuildRole.class); }
    @Override public void guildRoleUpdate(SatoriApiContext c, GuildRoleUpdate r) { postVoid(c, "guild.role.update", r); }
    @Override public void guildRoleDelete(SatoriApiContext c, GuildRoleRef r) { postVoid(c, "guild.role.delete", r); }
    @Override public SatoriPage<SatoriFriend> friendList(SatoriApiContext c, Cursor r) { return page(c, "friend.list", r, SatoriFriend.class); }
    @Override public void friendDelete(SatoriApiContext c, UserId r) { postVoid(c, "friend.delete", r); }
    @Override public void friendApprove(SatoriApiContext c, Approve r) { postVoid(c, "friend.approve", r); }
    @Override public void reactionCreate(SatoriApiContext c, Reaction r) { postVoid(c, "reaction.create", r); }
    @Override public void reactionDelete(SatoriApiContext c, Reaction r) { postVoid(c, "reaction.delete", r); }
    @Override public void reactionClear(SatoriApiContext c, Reaction r) { postVoid(c, "reaction.clear", r); }
    @Override public SatoriPage<SatoriUser> reactionList(SatoriApiContext c, Reaction r) { return page(c, "reaction.list", r, SatoriUser.class); }
    @Override public SatoriLogin loginGet(SatoriApiContext c) { return post(c, "login.get", null, SatoriLogin.class); }
    @Override public SatoriMeta meta(SatoriApiContext c) { return execute(c, "meta", null, SatoriMeta.class, false); }
    @Override public void webhookCreate(SatoriApiContext c, WebhookCreate r) { postVoid(c, "meta/webhook.create", r); }
    @Override public void webhookDelete(SatoriApiContext c, WebhookDelete r) { postVoid(c, "meta/webhook.delete", r); }

    @Override
    public Map<String, String> uploadCreate(SatoriApiContext context, List<UploadFile> files) {
        if (files == null || files.isEmpty()) throw new BizException("至少需要上传一个文件");
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        for (UploadFile file : files) {
            ByteArrayResource resource = new ByteArrayResource(file.content()) { @Override public String getFilename() { return file.filename(); } };
            parts.add(file.fieldName(), resource);
        }
        String body = client(context, true).post().uri("/v1/upload.create")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData(parts))
                .retrieve().onStatus(s -> s.isError(), response -> response.bodyToMono(String.class).map(bodyText -> SatoriHttpErrorMapper.toException(response.statusCode(), bodyText)))
                .bodyToMono(String.class).block(REQUEST_TIMEOUT);
        return read(body, mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
    }

    @Override
    public ProxyResource proxy(SatoriApiContext context, String url) {
        URI resource = safeProxyUrl(url);
        String path = "/v1/proxy/" + URLEncoder.encode(resource.toString(), StandardCharsets.UTF_8).replace("+", "%20");
        return client(context, false).get().uri(path).exchangeToMono(response -> {
            if (response.statusCode().isError()) return response.bodyToMono(String.class).map(body -> { throw SatoriHttpErrorMapper.toException(response.statusCode(), body); });
            String type = response.headers().contentType().map(MediaType::toString).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            return response.bodyToMono(byte[].class).map(body -> new ProxyResource(type, body));
        }).block(REQUEST_TIMEOUT);
    }

    @Override
    public Object invoke(SatoriApiContext context, InternalRequest request) {
        if (request == null || request.method() == null || !INTERNAL_METHOD.matcher(request.method()).matches()) {
            throw new BizException("Satori 内部方法名称不合法");
        }
        return execute(context, "internal/" + request.method(), request.body(), Object.class, true);
    }

    private <T> T post(SatoriApiContext c, String method, Object body, Class<T> type) { return execute(c, method, body, type, true); }
    private void postVoid(SatoriApiContext c, String method, Object body) { execute(c, method, body, Void.class, true); }
    private <T> List<T> list(SatoriApiContext c, String method, Object body, Class<T> type) { return execute(c, method, body, mapper.getTypeFactory().constructCollectionType(List.class, type), true); }
    private <T> SatoriPage<T> page(SatoriApiContext c, String method, Object body, Class<T> type) { return execute(c, method, body, mapper.getTypeFactory().constructParametricType(SatoriPage.class, type), true); }
    private <T> SatoriBidiPage<T> bidiPage(SatoriApiContext c, String method, Object body, Class<T> type) { return execute(c, method, body, mapper.getTypeFactory().constructParametricType(SatoriBidiPage.class, type), true); }
    private <T> T execute(SatoriApiContext context, String method, Object body, Class<T> type, boolean accountHeaders) { return execute(context, method, body, mapper.constructType(type), accountHeaders); }
    private <T> T execute(SatoriApiContext context, String method, Object body, JavaType type, boolean accountHeaders) {
        WebClient.RequestHeadersSpec<?> request = client(context, accountHeaders).post().uri("/v1/" + method)
                .contentType(MediaType.APPLICATION_JSON)
                // Some Satori adapters parse every POST body, including login.get.
                .bodyValue(body == null ? Map.of() : body);
        String response = request.retrieve().onStatus(s -> s.isError(), r -> r.bodyToMono(String.class).map(text -> SatoriHttpErrorMapper.toException(r.statusCode(), text))).bodyToMono(String.class).block(REQUEST_TIMEOUT);
        if (type.getRawClass() == Void.class || response == null || response.isBlank()) return null;
        return read(response, type);
    }
    private WebClient client(SatoriApiContext context, boolean accountHeaders) {
        URI base = safeBaseUrl(context);
        WebClient.Builder builder = WebClient.builder().baseUrl(base.toString()).clientConnector(new ReactorClientHttpConnector(HttpClient.create().responseTimeout(REQUEST_TIMEOUT)))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + required(context.token(), "Satori Token 不能为空"));
        if (accountHeaders) builder.defaultHeader("Satori-Platform", required(context.platform(), "Satori 平台不能为空")).defaultHeader("Satori-User-ID", required(context.userId(), "Satori 用户不能为空"));
        return builder.build();
    }
    private <T> T read(String body, JavaType type) { try { return mapper.readValue(body, type); } catch (JsonProcessingException ex) { throw new BizException("Satori 响应 JSON 无法解析"); } }
    private URI safeBaseUrl(SatoriApiContext context) {
        if (context == null) throw new BizException("Satori API 上下文不能为空");
        try { URI uri = URI.create(required(context.baseUrl(), "Satori 地址不能为空")); if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null) return uri; } catch (IllegalArgumentException ignored) { }
        throw new BizException("Satori 地址必须是有效的 HTTP 地址");
    }
    private URI safeProxyUrl(String url) {
        try { URI uri = URI.create(required(url, "代理资源地址不能为空")); if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null) return uri; } catch (IllegalArgumentException ignored) { }
        throw new BizException("代理资源地址必须是有效的 HTTP 地址");
    }
    private String required(String value, String message) { if (value == null || value.isBlank()) throw new BizException(message); return value.trim(); }
}
