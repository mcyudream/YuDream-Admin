package online.yudream.base.application.system.security;

import online.yudream.base.application.system.security.cmd.OAuthAuthorizeCmd;
import online.yudream.base.application.system.security.dto.OAuthAuthorizationDTO;
import online.yudream.base.application.system.security.service.OAuthServerAppService;
import online.yudream.base.application.system.user.service.PermissionAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.aggregate.OAuthAuthorizationCode;
import online.yudream.base.domain.system.security.aggregate.OAuthClientRegistration;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthGrantType;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;
import online.yudream.base.domain.system.security.repo.ApiSecurityPolicyRepo;
import online.yudream.base.domain.system.security.repo.OAuthAccessTokenRepo;
import online.yudream.base.domain.system.security.repo.OAuthAuthorizationCodeRepo;
import online.yudream.base.domain.system.security.repo.OAuthClientRegistrationRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthServerAppServiceTest {

    private static final long USER_ID = 1001L;
    private static final String CLIENT_ID = "ymcl";
    private static final String REDIRECT_URI = "ymcl://auth/callback";
    private static final List<String> CLIENT_SCOPES = List.of(
            "openid",
            "profile",
            "plugin:launcher-adapter:view",
            "plugin:launcher-adapter:manage"
    );

    @Mock
    private ApiSecurityPolicyRepo policyRepo;
    @Mock
    private OAuthClientRegistrationRepo clientRepo;
    @Mock
    private OAuthAuthorizationCodeRepo authorizationCodeRepo;
    @Mock
    private OAuthAccessTokenRepo accessTokenRepo;
    @Mock
    private PermissionAppService permissionAppService;

    private OAuthServerAppService service;

    @BeforeEach
    void setUp() {
        service = new OAuthServerAppService(
                policyRepo,
                clientRepo,
                authorizationCodeRepo,
                accessTokenRepo,
                permissionAppService
        );
        ApiSecurityPolicy policy = ApiSecurityPolicy.createDefault();
        policy.updateSwitches(false, false, false, false, true, false);
        when(policyRepo.findDefault()).thenReturn(Optional.of(policy));
        when(clientRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(ymclClient()));
        lenient().when(authorizationCodeRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void superAdminReceivesRequestedPluginScopes() {
        when(permissionAppService.getUserPermissions(USER_ID)).thenReturn(List.of("*"));

        service.authorize(authorizeCmd("openid profile plugin:launcher-adapter:view plugin:launcher-adapter:manage"));

        assertThat(capturedScopes()).containsExactly(
                "openid",
                "profile",
                "plugin:launcher-adapter:view",
                "plugin:launcher-adapter:manage"
        );
    }

    @Test
    void ordinaryUserOnlyReceivesOwnedPluginScopes() {
        when(permissionAppService.getUserPermissions(USER_ID)).thenReturn(List.of("plugin:launcher-adapter:view"));

        service.authorize(authorizeCmd("openid profile plugin:launcher-adapter:view plugin:launcher-adapter:manage"));

        assertThat(capturedScopes()).containsExactly("openid", "profile", "plugin:launcher-adapter:view");
    }

    @Test
    void identityScopesRemainWhenUserHasNoPluginPermission() {
        when(permissionAppService.getUserPermissions(USER_ID)).thenReturn(List.of());

        OAuthAuthorizationDTO dto = service.authorize(authorizeCmd("openid profile plugin:launcher-adapter:manage"));

        assertThat(capturedScopes()).containsExactly("openid", "profile");
        assertThat(dto.getRedirectUrl()).contains("code=");
        verify(permissionAppService).getUserPermissions(USER_ID);
    }

    @Test
    void rejectsEmptyGrantedScope() {
        when(permissionAppService.getUserPermissions(USER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.authorize(authorizeCmd("plugin:launcher-adapter:manage")))
                .isInstanceOf(BizException.class)
                .hasMessage("OAuth 授权范围不能为空");
    }

    private List<String> capturedScopes() {
        ArgumentCaptor<OAuthAuthorizationCode> captor = ArgumentCaptor.forClass(OAuthAuthorizationCode.class);
        verify(authorizationCodeRepo).save(captor.capture());
        return captor.getValue().getScopes();
    }

    private OAuthAuthorizeCmd authorizeCmd(String scope) {
        OAuthAuthorizeCmd cmd = new OAuthAuthorizeCmd();
        cmd.setResponseType("code");
        cmd.setClientId(CLIENT_ID);
        cmd.setRedirectUri(REDIRECT_URI);
        cmd.setScope(scope);
        cmd.setState("state-1");
        cmd.setUserId(USER_ID);
        return cmd;
    }

    private static OAuthClientRegistration ymclClient() {
        return OAuthClientRegistration.builder()
                .clientId(CLIENT_ID)
                .clientName("YMCL")
                .authMethod(OAuthClientAuthMethod.NONE)
                .grantTypes(List.of(OAuthGrantType.AUTHORIZATION_CODE, OAuthGrantType.REFRESH_TOKEN))
                .redirectUris(List.of(REDIRECT_URI))
                .scopes(CLIENT_SCOPES)
                .accessTokenTtlSeconds(7200)
                .refreshTokenTtlSeconds(604800)
                .status(OAuthRegistrationStatus.ACTIVE)
                .build();
    }
}
