package online.yudream.base.domain.platform.milky.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;
import online.yudream.base.domain.platform.milky.model.MilkyModels;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MilkyConnection extends BaseDomain {
    private String name;
    private MilkyConnectionProtocol protocol;
    private String baseUrl;
    @ToString.Exclude
    private String token;
    private String appId;
    @ToString.Exclude
    private String appSecret;
    private boolean sandbox;
    private Integer intents;
    /** 机器人在群内被 @ 时的提及 openid（全量群消息 GROUP_MESSAGE_CREATE 靠它与内容 <@id> 匹配判定定向；不同群可能不同，逐个登记）。 */
    private List<String> mentionOpenIds;
    private boolean enabled;
    private String commandMenuImageMode;
    private String commandMenuPublicBaseUrl;

    public static MilkyConnection create(String name, String baseUrl, String token, String commandMenuImageMode, String commandMenuPublicBaseUrl) {
        return create(name, MilkyConnectionProtocol.MILKY.code(), baseUrl, token, null, null, false, null,
                commandMenuImageMode, commandMenuPublicBaseUrl);
    }

    public static MilkyConnection create(String name, String protocol, String baseUrl, String token,
                                         String appId, String appSecret, Boolean sandbox, Integer intents,
                                         String commandMenuImageMode, String commandMenuPublicBaseUrl) {
        return create(name, protocol, baseUrl, token, appId, appSecret, sandbox, intents,
                commandMenuImageMode, commandMenuPublicBaseUrl, null);
    }

    public static MilkyConnection create(String name, String protocol, String baseUrl, String token,
                                         String appId, String appSecret, Boolean sandbox, Integer intents,
                                         String commandMenuImageMode, String commandMenuPublicBaseUrl,
                                         List<String> mentionOpenIds) {
        MilkyConnectionProtocol kind = MilkyConnectionProtocol.from(protocol);
        MilkyConnectionBuilder<?, ?> builder = MilkyConnection.builder()
                .name(required(name, "连接名称不能为空"))
                .protocol(kind)
                .sandbox(Boolean.TRUE.equals(sandbox))
                .intents(intents)
                .mentionOpenIds(sanitizeMentionOpenIds(mentionOpenIds))
                .enabled(true)
                .commandMenuImageMode(normalizeMode(commandMenuImageMode))
                .commandMenuPublicBaseUrl(blank(commandMenuPublicBaseUrl) ? null : commandMenuPublicBaseUrl.trim());
        if (kind.official()) {
            boolean sandboxMode = Boolean.TRUE.equals(sandbox);
            String resolvedBase = blank(baseUrl)
                    ? (sandboxMode ? MilkyConnectionProtocol.OFFICIAL_SANDBOX_API : MilkyConnectionProtocol.OFFICIAL_API)
                    : baseUrl.replaceAll("/+$", "");
            return builder
                    .baseUrl(resolvedBase)
                    .appId(required(appId, "AppID 不能为空"))
                    .appSecret(required(appSecret, "AppSecret 不能为空"))
                    .build();
        }
        return builder
                .baseUrl(required(baseUrl, "Milky 地址不能为空").replaceAll("/+$", ""))
                .token(required(token, "Access Token 不能为空"))
                .build();
    }

    public void update(String name, String baseUrl, String token, String commandMenuImageMode, String commandMenuPublicBaseUrl) {
        update(name, protocolCode(), baseUrl, token, appId, null, sandbox, intents, commandMenuImageMode, commandMenuPublicBaseUrl,
                mentionOpenIds);
    }

    public void update(String name, String protocol, String baseUrl, String token,
                       String appId, String appSecret, Boolean sandbox, Integer intents,
                       String commandMenuImageMode, String commandMenuPublicBaseUrl) {
        update(name, protocol, baseUrl, token, appId, appSecret, sandbox, intents,
                commandMenuImageMode, commandMenuPublicBaseUrl, null);
    }

    public void update(String name, String protocol, String baseUrl, String token,
                       String appId, String appSecret, Boolean sandbox, Integer intents,
                       String commandMenuImageMode, String commandMenuPublicBaseUrl,
                       List<String> mentionOpenIds) {
        this.name = required(name, "连接名称不能为空");
        MilkyConnectionProtocol kind = protocol == null || protocol.isBlank()
                ? protocolOrDefault()
                : MilkyConnectionProtocol.from(protocol);
        this.protocol = kind;
        this.commandMenuImageMode = normalizeMode(commandMenuImageMode);
        this.commandMenuPublicBaseUrl = blank(commandMenuPublicBaseUrl) ? null : commandMenuPublicBaseUrl.trim();
        if (intents != null) {
            this.intents = intents;
        }
        if (mentionOpenIds != null) {
            this.mentionOpenIds = sanitizeMentionOpenIds(mentionOpenIds);
        }
        if (sandbox != null) {
            this.sandbox = sandbox;
        }
        if (kind.official()) {
            if (!blank(appId)) {
                this.appId = appId.trim();
            }
            if (!blank(appSecret)) {
                this.appSecret = appSecret.trim();
            }
            if (blank(this.appId)) {
                throw new BizException("AppID 不能为空");
            }
            if (blank(this.appSecret)) {
                throw new BizException("AppSecret 不能为空");
            }
            if (!blank(baseUrl)) {
                this.baseUrl = baseUrl.replaceAll("/+$", "");
            } else if (blank(this.baseUrl)) {
                this.baseUrl = this.sandbox ? MilkyConnectionProtocol.OFFICIAL_SANDBOX_API : MilkyConnectionProtocol.OFFICIAL_API;
            }
            return;
        }
        if (!blank(baseUrl)) {
            this.baseUrl = baseUrl.replaceAll("/+$", "");
        }
        if (blank(this.baseUrl)) {
            throw new BizException("Milky 地址不能为空");
        }
        if (!blank(token)) {
            this.token = token.trim();
        }
        if (blank(this.token)) {
            throw new BizException("Access Token 不能为空");
        }
    }

    public MilkyConnectionProtocol protocolOrDefault() {
        return protocol == null ? MilkyConnectionProtocol.MILKY : protocol;
    }

    public String protocolCode() {
        return protocolOrDefault().code();
    }

    public boolean official() {
        return protocolOrDefault().official();
    }

    public int officialIntents() {
        return intents == null ? MilkyConnectionProtocol.DEFAULT_OFFICIAL_INTENTS : intents;
    }

    public List<String> officialMentionOpenIds() {
        return mentionOpenIds == null ? List.of() : mentionOpenIds;
    }

    /** 登记机器人被 @ 时的提及 openid（官方全量群消息按它匹配定向）；重复登记自动去重。 */
    public void bindMentionOpenId(String mentionOpenId) {
        if (blank(mentionOpenId)) {
            throw new BizException("机器人提及 openid 不能为空");
        }
        String trimmed = mentionOpenId.trim();
        List<String> ids = new java.util.ArrayList<>(officialMentionOpenIds());
        if (!ids.contains(trimmed)) {
            ids.add(trimmed);
        }
        this.mentionOpenIds = ids;
    }

    private static List<String> sanitizeMentionOpenIds(List<String> mentionOpenIds) {
        if (mentionOpenIds == null) {
            return null;
        }
        return mentionOpenIds.stream()
                .filter(id -> !blank(id))
                .map(String::trim)
                .distinct()
                .toList();
    }

    public boolean credentialConfigured() {
        return official() ? !blank(appSecret) && !blank(appId) : !blank(token);
    }

    public MilkyModels.Context toApiContext() {
        return new MilkyModels.Context(protocolCode(), baseUrl, token, appId, appSecret, sandbox, null, getId());
    }

    private static String normalizeMode(String mode) {
        return "url".equalsIgnoreCase(mode) ? "url" : "base64";
    }

    private static String required(String value, String message) {
        if (blank(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
