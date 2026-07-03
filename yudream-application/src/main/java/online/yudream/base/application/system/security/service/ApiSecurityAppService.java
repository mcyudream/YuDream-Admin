package online.yudream.base.application.system.security.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.assembler.ApiSecurityAssembler;
import online.yudream.base.application.system.security.cmd.ApiKeyCreateCmd;
import online.yudream.base.application.system.security.cmd.ApiKeyRevokeCmd;
import online.yudream.base.application.system.security.cmd.ApiSecurityPolicyUpdateCmd;
import online.yudream.base.application.system.security.dto.ApiKeyCreateResultDTO;
import online.yudream.base.application.system.security.dto.ApiKeyCredentialDTO;
import online.yudream.base.application.system.security.dto.ApiSecurityPolicyDTO;
import online.yudream.base.application.system.security.query.ApiKeyPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.ApiKeyCredential;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.repo.ApiKeyCredentialRepo;
import online.yudream.base.domain.system.security.repo.ApiSecurityPolicyRepo;
import online.yudream.base.domain.system.security.service.ApiKeyPermissionPolicy;
import online.yudream.base.domain.system.security.valobj.ApiKeySecret;
import online.yudream.base.domain.system.security.valobj.PermissionScope;
import online.yudream.base.domain.system.security.valobj.TokenPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiSecurityAppService {

    private static final String KEY_PREFIX = "yda_";
    private static final int SECRET_BYTES = 32;

    private final ApiSecurityPolicyRepo apiSecurityPolicyRepo;
    private final ApiKeyCredentialRepo apiKeyCredentialRepo;
    private final ApiKeyPermissionPolicy apiKeyPermissionPolicy = new ApiKeyPermissionPolicy();
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public ApiSecurityPolicyDTO policy() {
        return ApiSecurityAssembler.toDTO(currentPolicy());
    }

    @Transactional
    public ApiSecurityPolicyDTO updatePolicy(ApiSecurityPolicyUpdateCmd cmd) {
        ApiSecurityPolicy policy = currentPolicy();
        policy.updateSwitches(
                cmd.isApiEncryptionEnabled(),
                cmd.isDualTokenEnabled(),
                cmd.isApiKeyEnabled(),
                cmd.isPasskeyEnabled(),
                cmd.isOauthServerEnabled(),
                cmd.isOauthClientEnabled()
        );
        policy.updateTokenPolicy(new TokenPolicy(
                cmd.getAccessTokenTtlSeconds(),
                cmd.getRefreshTokenTtlSeconds(),
                cmd.isRefreshRotationEnabled()
        ));
        return ApiSecurityAssembler.toDTO(apiSecurityPolicyRepo.save(policy));
    }

    @Transactional(readOnly = true)
    public PageResult<ApiKeyCredentialDTO> pageApiKeys(ApiKeyPageQuery query) {
        int page = query == null ? 1 : query.getPage();
        int size = query == null ? 10 : query.getSize();
        String keyword = query == null ? null : query.getKeyword();
        List<ApiKeyCredentialDTO> records = apiKeyCredentialRepo.page(keyword, page, size).stream()
                .peek(credential -> credential.refreshExpiryStatus(LocalDateTime.now()))
                .map(ApiSecurityAssembler::toDTO)
                .toList();
        return new PageResult<>(records, apiKeyCredentialRepo.count(keyword), Math.max(page, 1), Math.max(size, 1));
    }

    @Transactional
    public ApiKeyCreateResultDTO createApiKey(ApiKeyCreateCmd cmd) {
        PermissionScope scope = new PermissionScope(cmd.getPermissions());
        apiKeyPermissionPolicy.validateCreatorScope(scope, cmd.getCreatorPermissions(), cmd.isSuperAdmin());
        String plaintext = generatePlaintext();
        String prefix = prefixOf(plaintext);
        ApiKeyCredential credential = ApiKeyCredential.create(
                cmd.getName(),
                new ApiKeySecret(prefix, ApiKeySecretHasher.hash(plaintext), ApiKeySecret.mask(prefix)),
                cmd.getCreatorUserId(),
                scope,
                cmd.getExpireTime()
        );
        ApiKeyCredential saved = apiKeyCredentialRepo.save(credential);
        return ApiKeyCreateResultDTO.builder()
                .credential(ApiSecurityAssembler.toDTO(saved))
                .plaintext(plaintext)
                .build();
    }

    @Transactional
    public ApiKeyCredentialDTO revokeApiKey(ApiKeyRevokeCmd cmd) {
        ApiKeyCredential credential = apiKeyCredentialRepo.findById(cmd.getId())
                .orElseThrow(() -> new BizException("API Key 不存在"));
        credential.revoke();
        return ApiSecurityAssembler.toDTO(apiKeyCredentialRepo.save(credential));
    }

    private ApiSecurityPolicy currentPolicy() {
        return apiSecurityPolicyRepo.findDefault()
                .orElseGet(() -> apiSecurityPolicyRepo.save(ApiSecurityPolicy.createDefault()));
    }

    private String generatePlaintext() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return KEY_PREFIX + HexFormat.of().formatHex(bytes);
    }

    private String prefixOf(String plaintext) {
        int length = Math.min(12, plaintext.length());
        return plaintext.substring(0, length);
    }

}
