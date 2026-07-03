package online.yudream.base.interfaces.system.security.assembler;

import online.yudream.base.application.system.security.cmd.ApiKeyCreateCmd;
import online.yudream.base.application.system.security.cmd.ApiKeyRevokeCmd;
import online.yudream.base.application.system.security.cmd.ApiSecurityPolicyUpdateCmd;
import online.yudream.base.application.system.security.cmd.OAuthClientSaveCmd;
import online.yudream.base.application.system.security.cmd.OAuthProviderSaveCmd;
import online.yudream.base.application.system.security.cmd.PasskeyRevokeCmd;
import online.yudream.base.application.system.security.dto.ApiKeyCreateResultDTO;
import online.yudream.base.application.system.security.dto.ApiKeyCredentialDTO;
import online.yudream.base.application.system.security.dto.ApiSecurityPolicyDTO;
import online.yudream.base.application.system.security.dto.OAuthClientCreateResultDTO;
import online.yudream.base.application.system.security.dto.OAuthClientDTO;
import online.yudream.base.application.system.security.dto.OAuthProviderDTO;
import online.yudream.base.application.system.security.dto.PasskeyCredentialDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.system.security.request.ApiKeyCreateRequest;
import online.yudream.base.interfaces.system.security.request.ApiSecurityPolicyUpdateRequest;
import online.yudream.base.interfaces.system.security.request.OAuthClientSaveRequest;
import online.yudream.base.interfaces.system.security.request.OAuthProviderSaveRequest;
import online.yudream.base.interfaces.system.security.res.ApiKeyCreateResultRes;
import online.yudream.base.interfaces.system.security.res.ApiKeyCredentialRes;
import online.yudream.base.interfaces.system.security.res.ApiSecurityPolicyRes;
import online.yudream.base.interfaces.system.security.res.OAuthClientCreateResultRes;
import online.yudream.base.interfaces.system.security.res.OAuthClientRes;
import online.yudream.base.interfaces.system.security.res.OAuthProviderRes;
import online.yudream.base.interfaces.system.security.res.PasskeyCredentialRes;

import java.util.List;

public class ApiSecurityWebAssembler {

    private static final String ALL_PERMISSION = "*";

    private ApiSecurityWebAssembler() {
    }

    public static ApiSecurityPolicyUpdateCmd toCmd(ApiSecurityPolicyUpdateRequest request) {
        ApiSecurityPolicyUpdateCmd cmd = new ApiSecurityPolicyUpdateCmd();
        cmd.setApiEncryptionEnabled(request.isApiEncryptionEnabled());
        cmd.setDualTokenEnabled(request.isDualTokenEnabled());
        cmd.setApiKeyEnabled(request.isApiKeyEnabled());
        cmd.setPasskeyEnabled(request.isPasskeyEnabled());
        cmd.setOauthServerEnabled(request.isOauthServerEnabled());
        cmd.setOauthClientEnabled(request.isOauthClientEnabled());
        cmd.setAccessTokenTtlSeconds(request.getAccessTokenTtlSeconds());
        cmd.setRefreshTokenTtlSeconds(request.getRefreshTokenTtlSeconds());
        cmd.setRefreshRotationEnabled(request.isRefreshRotationEnabled());
        return cmd;
    }

    public static ApiKeyCreateCmd toCmd(ApiKeyCreateRequest request, Long creatorUserId, List<String> creatorPermissions) {
        ApiKeyCreateCmd cmd = new ApiKeyCreateCmd();
        cmd.setName(request.getName());
        cmd.setPermissions(request.getPermissions());
        cmd.setExpireTime(request.getExpireTime());
        cmd.setCreatorUserId(creatorUserId);
        cmd.setCreatorPermissions(creatorPermissions);
        cmd.setSuperAdmin(creatorPermissions != null && creatorPermissions.contains(ALL_PERMISSION));
        return cmd;
    }

    public static ApiKeyRevokeCmd toRevokeCmd(Long id) {
        ApiKeyRevokeCmd cmd = new ApiKeyRevokeCmd();
        cmd.setId(id);
        return cmd;
    }

    public static OAuthClientSaveCmd toCmd(OAuthClientSaveRequest request) {
        return toCmd(null, request);
    }

    public static OAuthClientSaveCmd toCmd(Long id, OAuthClientSaveRequest request) {
        OAuthClientSaveCmd cmd = new OAuthClientSaveCmd();
        cmd.setId(id);
        cmd.setClientId(request.getClientId());
        cmd.setClientName(request.getClientName());
        cmd.setAuthMethod(request.getAuthMethod());
        cmd.setGrantTypes(request.getGrantTypes());
        cmd.setRedirectUris(request.getRedirectUris());
        cmd.setScopes(request.getScopes());
        cmd.setAccessTokenTtlSeconds(request.getAccessTokenTtlSeconds());
        cmd.setRefreshTokenTtlSeconds(request.getRefreshTokenTtlSeconds());
        cmd.setStatus(request.getStatus());
        return cmd;
    }

    public static OAuthProviderSaveCmd toCmd(OAuthProviderSaveRequest request) {
        return toCmd(null, request);
    }

    public static OAuthProviderSaveCmd toCmd(Long id, OAuthProviderSaveRequest request) {
        OAuthProviderSaveCmd cmd = new OAuthProviderSaveCmd();
        cmd.setId(id);
        cmd.setCode(request.getCode());
        cmd.setName(request.getName());
        cmd.setIssuerUri(request.getIssuerUri());
        cmd.setAuthorizationUri(request.getAuthorizationUri());
        cmd.setTokenUri(request.getTokenUri());
        cmd.setUserInfoUri(request.getUserInfoUri());
        cmd.setClientId(request.getClientId());
        cmd.setClientSecret(request.getClientSecret());
        cmd.setAuthMethod(request.getAuthMethod());
        cmd.setScopes(request.getScopes());
        cmd.setRedirectUri(request.getRedirectUri());
        cmd.setStatus(request.getStatus());
        return cmd;
    }

    public static PasskeyRevokeCmd toPasskeyRevokeCmd(Long id) {
        PasskeyRevokeCmd cmd = new PasskeyRevokeCmd();
        cmd.setId(id);
        return cmd;
    }

    public static PageResult<ApiKeyCredentialRes> toPage(PageResult<ApiKeyCredentialDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(ApiSecurityWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    public static ApiSecurityPolicyRes toRes(ApiSecurityPolicyDTO dto) {
        return ApiSecurityPolicyRes.builder()
                .id(dto.getId())
                .apiEncryptionEnabled(dto.isApiEncryptionEnabled())
                .dualTokenEnabled(dto.isDualTokenEnabled())
                .apiKeyEnabled(dto.isApiKeyEnabled())
                .passkeyEnabled(dto.isPasskeyEnabled())
                .oauthServerEnabled(dto.isOauthServerEnabled())
                .oauthClientEnabled(dto.isOauthClientEnabled())
                .accessTokenTtlSeconds(dto.getAccessTokenTtlSeconds())
                .refreshTokenTtlSeconds(dto.getRefreshTokenTtlSeconds())
                .refreshRotationEnabled(dto.isRefreshRotationEnabled())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static ApiKeyCredentialRes toRes(ApiKeyCredentialDTO dto) {
        return ApiKeyCredentialRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .keyPrefix(dto.getKeyPrefix())
                .maskedValue(dto.getMaskedValue())
                .creatorUserId(dto.getCreatorUserId())
                .permissions(dto.getPermissions())
                .expireTime(dto.getExpireTime())
                .status(dto.getStatus())
                .lastUsedTime(dto.getLastUsedTime())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static ApiKeyCreateResultRes toRes(ApiKeyCreateResultDTO dto) {
        return ApiKeyCreateResultRes.builder()
                .credential(toRes(dto.getCredential()))
                .plaintext(dto.getPlaintext())
                .build();
    }

    public static OAuthClientRes toRes(OAuthClientDTO dto) {
        return OAuthClientRes.builder()
                .id(dto.getId())
                .clientId(dto.getClientId())
                .clientName(dto.getClientName())
                .authMethod(dto.getAuthMethod())
                .grantTypes(dto.getGrantTypes())
                .redirectUris(dto.getRedirectUris())
                .scopes(dto.getScopes())
                .accessTokenTtlSeconds(dto.getAccessTokenTtlSeconds())
                .refreshTokenTtlSeconds(dto.getRefreshTokenTtlSeconds())
                .status(dto.getStatus())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static OAuthClientCreateResultRes toRes(OAuthClientCreateResultDTO dto) {
        return OAuthClientCreateResultRes.builder()
                .client(toRes(dto.getClient()))
                .clientSecret(dto.getClientSecret())
                .build();
    }

    public static OAuthProviderRes toRes(OAuthProviderDTO dto) {
        return OAuthProviderRes.builder()
                .id(dto.getId())
                .code(dto.getCode())
                .name(dto.getName())
                .issuerUri(dto.getIssuerUri())
                .authorizationUri(dto.getAuthorizationUri())
                .tokenUri(dto.getTokenUri())
                .userInfoUri(dto.getUserInfoUri())
                .clientId(dto.getClientId())
                .authMethod(dto.getAuthMethod())
                .scopes(dto.getScopes())
                .redirectUri(dto.getRedirectUri())
                .status(dto.getStatus())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static PasskeyCredentialRes toRes(PasskeyCredentialDTO dto) {
        return PasskeyCredentialRes.builder()
                .id(dto.getId())
                .userId(dto.getUserId())
                .credentialId(dto.getCredentialId())
                .deviceName(dto.getDeviceName())
                .status(dto.getStatus())
                .signCount(dto.getSignCount())
                .lastUsedTime(dto.getLastUsedTime())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }
}
