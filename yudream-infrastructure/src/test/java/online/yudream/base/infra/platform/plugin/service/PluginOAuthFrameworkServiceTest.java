package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.aggregate.OAuthClientRegistration;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthGrantType;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;
import online.yudream.base.domain.system.security.repo.ApiSecurityPolicyRepo;
import online.yudream.base.domain.system.security.repo.OAuthClientRegistrationRepo;
import online.yudream.base.plugin.spi.system.security.PluginOAuthPublicClientSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginOAuthFrameworkServiceTest {

    @Test
    void createsPublicClientOnFirstEnable() {
        Fixture fixture = new Fixture();

        var result = fixture.service.ensurePublicClient(new PluginOAuthPublicClientSpec(
                "ymcl",
                "YMCL",
                List.of("sjmcl://auth/callback"),
                List.of("openid", "profile")
        ));

        assertTrue(result.isPresent());
        assertEquals("ymcl", result.get().clientId());
        assertTrue(result.get().active());
        OAuthClientRegistration stored = fixture.clients.findByClientId("ymcl").orElseThrow();
        assertEquals(OAuthClientAuthMethod.NONE, stored.getAuthMethod());
        assertEquals(List.of(OAuthGrantType.AUTHORIZATION_CODE, OAuthGrantType.REFRESH_TOKEN), stored.getGrantTypes());
        assertEquals(List.of("sjmcl://auth/callback"), stored.getRedirectUris());
        assertEquals(OAuthRegistrationStatus.ACTIVE, stored.getStatus());
    }

    @Test
    void mergesRedirectsAndReactivatesExistingClient() {
        Fixture fixture = new Fixture();
        OAuthClientRegistration existing = OAuthClientRegistration.create("ymcl", "旧名称", null);
        existing.update(
                "旧名称",
                OAuthClientAuthMethod.CLIENT_SECRET_BASIC,
                List.of(OAuthGrantType.AUTHORIZATION_CODE),
                List.of("https://legacy.example/callback"),
                List.of("openid"),
                3600,
                86400,
                OAuthRegistrationStatus.DISABLED
        );
        fixture.clients.save(existing);

        var result = fixture.service.ensurePublicClient(new PluginOAuthPublicClientSpec(
                "ymcl",
                "YMCL",
                List.of("sjmcl://auth/callback"),
                List.of("openid", "profile")
        ));

        assertTrue(result.isPresent());
        OAuthClientRegistration stored = fixture.clients.findByClientId("ymcl").orElseThrow();
        assertEquals("YMCL", stored.getClientName());
        assertEquals(OAuthClientAuthMethod.NONE, stored.getAuthMethod());
        assertEquals(OAuthRegistrationStatus.ACTIVE, stored.getStatus());
        assertEquals(List.of("https://legacy.example/callback", "sjmcl://auth/callback"), stored.getRedirectUris());
        assertEquals(List.of("openid", "profile"), stored.getScopes());
        assertEquals(3600, stored.getAccessTokenTtlSeconds());
    }

    @Test
    void rejectsBlankClientId() {
        Fixture fixture = new Fixture();
        assertTrue(fixture.service.ensurePublicClient(new PluginOAuthPublicClientSpec(
                " ",
                "YMCL",
                List.of("sjmcl://auth/callback"),
                List.of("openid")
        )).isEmpty());
        assertTrue(fixture.clients.findAll().isEmpty());
    }

    private static final class Fixture {
        private final InMemoryClientRepo clients = new InMemoryClientRepo();
        private final PluginOAuthFrameworkService service = new PluginOAuthFrameworkService(
                new DisabledPolicyRepo(),
                clients
        );
    }

    private static final class DisabledPolicyRepo implements ApiSecurityPolicyRepo {
        @Override
        public ApiSecurityPolicy save(ApiSecurityPolicy policy) {
            return policy;
        }

        @Override
        public Optional<ApiSecurityPolicy> findDefault() {
            return Optional.empty();
        }
    }

    private static final class InMemoryClientRepo implements OAuthClientRegistrationRepo {
        private final AtomicLong ids = new AtomicLong(1);
        private final List<OAuthClientRegistration> records = new ArrayList<>();

        @Override
        public OAuthClientRegistration save(OAuthClientRegistration registration) {
            if (registration.getId() == null) {
                registration.setId(ids.getAndIncrement());
            }
            records.removeIf(item -> item.getClientId().equals(registration.getClientId()));
            records.add(registration);
            return registration;
        }

        @Override
        public Optional<OAuthClientRegistration> findById(Long id) {
            return records.stream().filter(item -> id.equals(item.getId())).findFirst();
        }

        @Override
        public Optional<OAuthClientRegistration> findByClientId(String clientId) {
            return records.stream().filter(item -> item.getClientId().equals(clientId)).findFirst();
        }

        @Override
        public List<OAuthClientRegistration> findAll() {
            return List.copyOf(records);
        }
    }
}
