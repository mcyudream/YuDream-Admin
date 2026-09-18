package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.aggregate.OAuthClientRegistration;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthGrantType;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;
import online.yudream.base.domain.system.security.repo.ApiSecurityPolicyRepo;
import online.yudream.base.domain.system.security.repo.OAuthClientRegistrationRepo;
import online.yudream.base.plugin.spi.system.security.PluginOAuthClient;
import online.yudream.base.plugin.spi.system.security.PluginOAuthPublicClientSpec;
import online.yudream.base.plugin.spi.system.security.PluginOAuthService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PluginOAuthFrameworkService implements PluginOAuthService {

    private static final List<OAuthGrantType> PUBLIC_GRANTS = List.of(
            OAuthGrantType.AUTHORIZATION_CODE,
            OAuthGrantType.REFRESH_TOKEN
    );
    private static final List<String> DEFAULT_SCOPES = List.of("openid", "profile");
    private static final long DEFAULT_ACCESS_TTL = 7200L;
    private static final long DEFAULT_REFRESH_TTL = 604800L;

    private final ApiSecurityPolicyRepo apiSecurityPolicyRepo;
    private final OAuthClientRegistrationRepo oauthClientRegistrationRepo;

    @Override
    public boolean enabled() {
        return apiSecurityPolicyRepo.findDefault()
                .orElseGet(ApiSecurityPolicy::createDefault)
                .isOauthServerEnabled();
    }

    @Override
    public Optional<PluginOAuthClient> findClient(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return Optional.empty();
        }
        return oauthClientRegistrationRepo.findByClientId(clientId.trim()).map(this::toClient);
    }

    @Override
    public Optional<PluginOAuthClient> ensurePublicClient(PluginOAuthPublicClientSpec spec) {
        if (spec == null || !StringUtils.hasText(spec.clientId()) || !StringUtils.hasText(spec.clientName())) {
            return Optional.empty();
        }
        String clientId = spec.clientId().trim();
        String clientName = spec.clientName().trim();
        List<String> redirectUris = normalize(spec.redirectUris());
        List<String> scopes = normalize(spec.scopes());
        if (redirectUris.isEmpty()) {
            return Optional.empty();
        }
        if (scopes.isEmpty()) {
            scopes = DEFAULT_SCOPES;
        }
        OAuthClientRegistration registration = oauthClientRegistrationRepo.findByClientId(clientId)
                .orElseGet(() -> OAuthClientRegistration.create(clientId, clientName, null));
        List<String> mergedRedirects = merge(registration.getRedirectUris(), redirectUris);
        List<String> mergedScopes = merge(registration.getScopes(), scopes);
        long accessTtl = registration.getAccessTokenTtlSeconds() > 0
                ? registration.getAccessTokenTtlSeconds()
                : DEFAULT_ACCESS_TTL;
        long refreshTtl = registration.getRefreshTokenTtlSeconds() > 0
                ? registration.getRefreshTokenTtlSeconds()
                : DEFAULT_REFRESH_TTL;
        registration.update(
                clientName,
                OAuthClientAuthMethod.NONE,
                PUBLIC_GRANTS,
                mergedRedirects,
                mergedScopes,
                accessTtl,
                refreshTtl,
                OAuthRegistrationStatus.ACTIVE
        );
        return Optional.of(toClient(oauthClientRegistrationRepo.save(registration)));
    }

    /** 仅停用登记（插件停用/卸载场景），保留回调地址与 scope 配置。 */
    @Override
    public void disableClient(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return;
        }
        oauthClientRegistrationRepo.findByClientId(clientId.trim())
                .filter(registration -> registration.getStatus() == OAuthRegistrationStatus.ACTIVE)
                .ifPresent(registration -> {
                    registration.disable();
                    oauthClientRegistrationRepo.save(registration);
                });
    }

    private PluginOAuthClient toClient(OAuthClientRegistration registration) {
        return new PluginOAuthClient(
                registration.getClientId(),
                registration.getClientName(),
                registration.getRedirectUris() == null ? List.of() : registration.getRedirectUris(),
                registration.getScopes() == null ? List.of() : registration.getScopes(),
                registration.getStatus() == OAuthRegistrationStatus.ACTIVE
        );
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                unique.add(value.trim());
            }
        }
        return List.copyOf(unique);
    }

    private static List<String> merge(List<String> existing, List<String> incoming) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (existing != null) {
            for (String value : existing) {
                if (StringUtils.hasText(value)) {
                    unique.add(value.trim());
                }
            }
        }
        unique.addAll(incoming);
        return new ArrayList<>(unique);
    }
}
