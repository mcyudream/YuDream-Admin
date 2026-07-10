package online.yudream.base.application.system.security;

import online.yudream.base.application.system.security.service.OAuthPasskeyAppService;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.aggregate.OAuthClientRegistration;
import online.yudream.base.domain.system.security.aggregate.OAuthProviderRegistration;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthGrantType;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;
import online.yudream.base.domain.system.security.repo.ApiSecurityPolicyRepo;
import online.yudream.base.domain.system.security.repo.OAuthClientRegistrationRepo;
import online.yudream.base.domain.system.security.repo.OAuthProviderRegistrationRepo;
import online.yudream.base.domain.system.security.repo.PasskeyCredentialRepo;
import online.yudream.base.domain.system.security.service.PasskeyCeremonyGateway;
import online.yudream.base.domain.system.user.repo.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthExplicitEnableTest {

    @Mock
    private OAuthClientRegistrationRepo clientRepo;
    @Mock
    private OAuthProviderRegistrationRepo providerRepo;
    @Mock
    private PasskeyCredentialRepo passkeyCredentialRepo;
    @Mock
    private ApiSecurityPolicyRepo policyRepo;
    @Mock
    private UserRepo userRepo;
    @Mock
    private PasskeyCeremonyGateway passkeyCeremonyGateway;

    private OAuthPasskeyAppService service;

    @BeforeEach
    void setUp() {
        service = new OAuthPasskeyAppService(
                clientRepo,
                providerRepo,
                passkeyCredentialRepo,
                policyRepo,
                userRepo,
                passkeyCeremonyGateway
        );
        ApiSecurityPolicy policy = ApiSecurityPolicy.createDefault();
        policy.updateSwitches(false, false, false, false, true, true);
        when(policyRepo.findDefault()).thenReturn(Optional.of(policy));
    }

    @Test
    void enableClientOnlyActivatesExistingRegistration() {
        OAuthClientRegistration registration = OAuthClientRegistration.builder()
                .id(11L)
                .clientId("admin-web")
                .clientName("Admin web")
                .clientSecretHash("secret-hash")
                .authMethod(OAuthClientAuthMethod.CLIENT_SECRET_BASIC)
                .grantTypes(List.of(OAuthGrantType.AUTHORIZATION_CODE))
                .redirectUris(List.of("https://admin.example/callback"))
                .scopes(List.of("openid", "profile"))
                .accessTokenTtlSeconds(3600)
                .refreshTokenTtlSeconds(86400)
                .status(OAuthRegistrationStatus.DISABLED)
                .build();
        when(clientRepo.findById(11L)).thenReturn(Optional.of(registration));
        when(clientRepo.save(registration)).thenReturn(registration);

        service.enableClient(11L);

        assertThat(registration.getStatus()).isEqualTo(OAuthRegistrationStatus.ACTIVE);
        assertThat(registration.getClientSecretHash()).isEqualTo("secret-hash");
        assertThat(registration.getRedirectUris()).containsExactly("https://admin.example/callback");
        assertThat(registration.getScopes()).containsExactly("openid", "profile");
        verify(clientRepo).save(same(registration));
    }

    @Test
    void enableProviderOnlyActivatesExistingRegistration() {
        OAuthProviderRegistration registration = OAuthProviderRegistration.builder()
                .id(12L)
                .code("github")
                .name("GitHub")
                .issuerUri("https://github.com")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .clientId("provider-client")
                .clientSecretHash("provider-secret")
                .authMethod(OAuthClientAuthMethod.CLIENT_SECRET_BASIC)
                .scopes(List.of("read:user", "user:email"))
                .redirectUri("https://admin.example/oauth/callback")
                .status(OAuthRegistrationStatus.DISABLED)
                .build();
        when(providerRepo.findById(12L)).thenReturn(Optional.of(registration));
        when(providerRepo.save(registration)).thenReturn(registration);

        service.enableProvider(12L);

        assertThat(registration.getStatus()).isEqualTo(OAuthRegistrationStatus.ACTIVE);
        assertThat(registration.getClientSecretHash()).isEqualTo("provider-secret");
        assertThat(registration.getAuthorizationUri()).isEqualTo("https://github.com/login/oauth/authorize");
        assertThat(registration.getScopes()).containsExactly("read:user", "user:email");
        verify(providerRepo).save(same(registration));
    }
}
