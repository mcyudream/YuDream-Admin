package online.yudream.base.interfaces.system.security.assembler;

import online.yudream.base.application.system.security.cmd.ApiKeyCreateCmd;
import online.yudream.base.application.system.security.cmd.ApiKeyRevokeCmd;
import online.yudream.base.application.system.security.cmd.ApiSecurityPolicyUpdateCmd;
import online.yudream.base.application.system.security.dto.ApiKeyCreateResultDTO;
import online.yudream.base.application.system.security.dto.ApiKeyCredentialDTO;
import online.yudream.base.application.system.security.dto.ApiSecurityPolicyDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.system.security.request.ApiKeyCreateRequest;
import online.yudream.base.interfaces.system.security.request.ApiSecurityPolicyUpdateRequest;
import online.yudream.base.interfaces.system.security.res.ApiKeyCreateResultRes;
import online.yudream.base.interfaces.system.security.res.ApiKeyCredentialRes;
import online.yudream.base.interfaces.system.security.res.ApiSecurityPolicyRes;

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
}
