package online.yudream.base.application.system.security.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.cmd.OAuthAuthorizeCmd;
import online.yudream.base.application.system.security.cmd.OAuthTokenCmd;
import online.yudream.base.application.system.security.dto.OAuthAuthorizationDTO;
import online.yudream.base.application.system.security.dto.OAuthTokenDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.aggregate.OAuthAccessToken;
import online.yudream.base.domain.system.security.aggregate.OAuthAuthorizationCode;
import online.yudream.base.domain.system.security.aggregate.OAuthClientRegistration;
import online.yudream.base.domain.system.security.enumerate.CredentialStatus;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthGrantType;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;
import online.yudream.base.domain.system.security.repo.ApiSecurityPolicyRepo;
import online.yudream.base.domain.system.security.repo.OAuthAccessTokenRepo;
import online.yudream.base.domain.system.security.repo.OAuthAuthorizationCodeRepo;
import online.yudream.base.domain.system.security.repo.OAuthClientRegistrationRepo;
import online.yudream.base.domain.system.security.valobj.TokenPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OAuthServerAppService {

    private static final String RESPONSE_TYPE_CODE = "code";
    private static final String GRANT_AUTHORIZATION_CODE = "authorization_code";
    private static final String GRANT_REFRESH_TOKEN = "refresh_token";
    private static final int CODE_BYTES = 24;
    private static final int TOKEN_BYTES = 32;

    private final ApiSecurityPolicyRepo apiSecurityPolicyRepo;
    private final OAuthClientRegistrationRepo oauthClientRegistrationRepo;
    private final OAuthAuthorizationCodeRepo oauthAuthorizationCodeRepo;
    private final OAuthAccessTokenRepo oauthAccessTokenRepo;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public OAuthAuthorizationDTO authorize(OAuthAuthorizeCmd cmd) {
        ensureServerEnabled();
        if (!RESPONSE_TYPE_CODE.equals(cmd.getResponseType())) {
            throw new BizException("OAuth response_type 仅支持 code");
        }
        OAuthClientRegistration client = activeClient(cmd.getClientId());
        ensureGrantAllowed(client, OAuthGrantType.AUTHORIZATION_CODE);
        ensureRedirectAllowed(client, cmd.getRedirectUri());
        List<String> scopes = resolveScopes(client, cmd.getScope());
        String code = randomValue("ydo_code_", CODE_BYTES);
        OAuthAuthorizationCode authorizationCode = OAuthAuthorizationCode.issue(
                code,
                client.getClientId(),
                cmd.getUserId(),
                cmd.getRedirectUri(),
                scopes,
                cmd.getState(),
                LocalDateTime.now().plusMinutes(5)
        );
        oauthAuthorizationCodeRepo.save(authorizationCode);
        String redirectUrl = redirectWithCode(cmd.getRedirectUri(), code, cmd.getState());
        return OAuthAuthorizationDTO.builder()
                .code(code)
                .state(cmd.getState())
                .redirectUri(cmd.getRedirectUri())
                .redirectUrl(redirectUrl)
                .build();
    }

    @Transactional
    public OAuthTokenDTO token(OAuthTokenCmd cmd) {
        ensureServerEnabled();
        if (GRANT_AUTHORIZATION_CODE.equals(cmd.getGrantType())) {
            return exchangeAuthorizationCode(cmd);
        }
        if (GRANT_REFRESH_TOKEN.equals(cmd.getGrantType())) {
            return refreshToken(cmd);
        }
        throw new BizException("OAuth grant_type 暂不支持");
    }

    private OAuthTokenDTO exchangeAuthorizationCode(OAuthTokenCmd cmd) {
        OAuthClientRegistration client = activeClient(cmd.getClientId());
        ensureClientAuthentication(client, cmd);
        ensureGrantAllowed(client, OAuthGrantType.AUTHORIZATION_CODE);
        OAuthAuthorizationCode authorizationCode = oauthAuthorizationCodeRepo.findByCode(cmd.getCode())
                .orElseThrow(() -> new BizException("OAuth 授权码无效或已过期"));
        authorizationCode.refreshExpiryStatus(LocalDateTime.now());
        if (!client.getClientId().equals(authorizationCode.getClientId())) {
            throw new BizException("OAuth 授权码无效或已过期");
        }
        if (!authorizationCode.getRedirectUri().equals(cmd.getRedirectUri())) {
            throw new BizException("OAuth 回调地址不匹配");
        }
        authorizationCode.use(LocalDateTime.now());
        oauthAuthorizationCodeRepo.save(authorizationCode);
        return issueToken(client, authorizationCode.getUserId(), authorizationCode.getScopes());
    }

    private OAuthTokenDTO refreshToken(OAuthTokenCmd cmd) {
        OAuthClientRegistration client = activeClient(cmd.getClientId());
        ensureClientAuthentication(client, cmd);
        ensureGrantAllowed(client, OAuthGrantType.REFRESH_TOKEN);
        OAuthAccessToken existing = oauthAccessTokenRepo.findByRefreshTokenHash(ApiKeySecretHasher.hash(cmd.getRefreshToken()))
                .orElseThrow(() -> new BizException("OAuth Refresh Token 无效或已过期"));
        if (!client.getClientId().equals(existing.getClientId()) || !existing.refreshActiveAt(LocalDateTime.now())) {
            throw new BizException("OAuth Refresh Token 无效或已过期");
        }
        if (currentPolicy().getTokenPolicy().refreshRotationEnabled()) {
            existing.revoke();
            oauthAccessTokenRepo.save(existing);
        }
        return issueToken(client, existing.getUserId(), existing.getScopes());
    }

    private OAuthTokenDTO issueToken(OAuthClientRegistration client, Long userId, List<String> scopes) {
        TokenPolicy tokenPolicy = currentPolicy().getTokenPolicy();
        long accessTtl = tokenPolicy.accessTokenTtlSeconds();
        long refreshTtl = tokenPolicy.refreshTokenTtlSeconds();
        String accessToken = randomValue("ydo_at_", TOKEN_BYTES);
        String refreshToken = randomValue("ydo_rt_", TOKEN_BYTES);
        OAuthAccessToken token = OAuthAccessToken.issue(
                ApiKeySecretHasher.hash(accessToken),
                ApiKeySecretHasher.hash(refreshToken),
                client.getClientId(),
                userId,
                scopes,
                LocalDateTime.now().plusSeconds(accessTtl),
                LocalDateTime.now().plusSeconds(refreshTtl)
        );
        token.setStatus(CredentialStatus.ACTIVE);
        oauthAccessTokenRepo.save(token);
        return OAuthTokenDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTtl)
                .scope(String.join(" ", scopes))
                .build();
    }

    private void ensureServerEnabled() {
        if (!currentPolicy().isOauthServerEnabled()) {
            throw new BizException("OAuth 服务端未启用");
        }
    }

    private ApiSecurityPolicy currentPolicy() {
        return apiSecurityPolicyRepo.findDefault().orElse(ApiSecurityPolicy.createDefault());
    }

    private OAuthClientRegistration activeClient(String clientId) {
        OAuthClientRegistration client = oauthClientRegistrationRepo.findByClientId(clientId)
                .orElseThrow(() -> new BizException("OAuth 客户端不存在"));
        if (client.getStatus() != OAuthRegistrationStatus.ACTIVE) {
            throw new BizException("OAuth 客户端已停用");
        }
        return client;
    }

    private void ensureClientAuthentication(OAuthClientRegistration client, OAuthTokenCmd cmd) {
        OAuthClientAuthMethod expected = client.getAuthMethod() == null
                ? OAuthClientAuthMethod.CLIENT_SECRET_BASIC
                : client.getAuthMethod();
        if (expected == OAuthClientAuthMethod.NONE) {
            return;
        }
        if (cmd.getAuthMethod() != expected) {
            throw new BizException(expected == OAuthClientAuthMethod.CLIENT_SECRET_BASIC
                    ? "OAuth 客户端必须使用 HTTP Basic 认证"
                    : "OAuth 客户端必须使用 client_secret 表单认证");
        }
        ensureClientSecret(client, cmd.getClientSecret());
    }

    private void ensureClientSecret(OAuthClientRegistration client, String clientSecret) {
        if (!StringUtils.hasText(clientSecret)
                || !ApiKeySecretHasher.hash(clientSecret).equals(client.getClientSecretHash())) {
            throw new BizException("OAuth 客户端密钥错误");
        }
    }

    private void ensureGrantAllowed(OAuthClientRegistration client, OAuthGrantType grantType) {
        if (client.getGrantTypes() == null || !client.getGrantTypes().contains(grantType)) {
            throw new BizException("OAuth 客户端不允许该授权模式");
        }
    }

    private void ensureRedirectAllowed(OAuthClientRegistration client, String redirectUri) {
        if (!StringUtils.hasText(redirectUri) || client.getRedirectUris() == null || !client.getRedirectUris().contains(redirectUri)) {
            throw new BizException("OAuth 回调地址不在客户端白名单内");
        }
    }

    private List<String> resolveScopes(OAuthClientRegistration client, String scope) {
        List<String> requested = StringUtils.hasText(scope)
                ? Arrays.stream(scope.trim().split("\\s+")).filter(StringUtils::hasText).toList()
                : client.getScopes();
        if (requested == null || requested.isEmpty()) {
            throw new BizException("OAuth 授权范围不能为空");
        }
        if (client.getScopes() == null || !client.getScopes().containsAll(requested)) {
            throw new BizException("OAuth 授权范围超出客户端允许范围");
        }
        return requested;
    }

    private String randomValue(String prefix, int bytesLength) {
        byte[] bytes = new byte[bytesLength];
        secureRandom.nextBytes(bytes);
        return prefix + HexFormat.of().formatHex(bytes);
    }

    private String redirectWithCode(String redirectUri, String code, String state) {
        StringBuilder builder = new StringBuilder(redirectUri);
        builder.append(redirectUri.contains("?") ? "&" : "?")
                .append("code=")
                .append(encode(code));
        if (StringUtils.hasText(state)) {
            builder.append("&state=").append(encode(state));
        }
        return builder.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
