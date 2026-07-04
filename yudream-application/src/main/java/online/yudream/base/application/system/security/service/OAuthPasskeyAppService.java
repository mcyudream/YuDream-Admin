package online.yudream.base.application.system.security.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.assembler.ApiSecurityAssembler;
import online.yudream.base.application.system.security.cmd.OAuthClientSaveCmd;
import online.yudream.base.application.system.security.cmd.OAuthProviderSaveCmd;
import online.yudream.base.application.system.security.cmd.PasskeyAuthenticationFinishCmd;
import online.yudream.base.application.system.security.cmd.PasskeyAuthenticationStartCmd;
import online.yudream.base.application.system.security.cmd.PasskeyRegistrationFinishCmd;
import online.yudream.base.application.system.security.cmd.PasskeyRegistrationStartCmd;
import online.yudream.base.application.system.security.cmd.PasskeyRevokeCmd;
import online.yudream.base.application.system.security.cmd.PasskeySelfRevokeCmd;
import online.yudream.base.application.system.security.dto.OAuthClientCreateResultDTO;
import online.yudream.base.application.system.security.dto.OAuthClientDTO;
import online.yudream.base.application.system.security.dto.OAuthProviderDTO;
import online.yudream.base.application.system.security.dto.PasskeyAuthenticationOptionsDTO;
import online.yudream.base.application.system.security.dto.PasskeyCredentialDTO;
import online.yudream.base.application.system.security.dto.PasskeyRegistrationOptionsDTO;
import online.yudream.base.application.system.security.query.PasskeyCredentialQuery;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.aggregate.OAuthClientRegistration;
import online.yudream.base.domain.system.security.aggregate.OAuthProviderRegistration;
import online.yudream.base.domain.system.security.aggregate.PasskeyCredential;
import online.yudream.base.domain.system.security.enumerate.CredentialStatus;
import online.yudream.base.domain.system.security.repo.ApiSecurityPolicyRepo;
import online.yudream.base.domain.system.security.repo.OAuthClientRegistrationRepo;
import online.yudream.base.domain.system.security.repo.OAuthProviderRegistrationRepo;
import online.yudream.base.domain.system.security.repo.PasskeyCredentialRepo;
import online.yudream.base.domain.system.security.service.PasskeyCeremonyGateway;
import online.yudream.base.domain.system.security.valobj.PasskeyAuthenticationOptions;
import online.yudream.base.domain.system.security.valobj.PasskeyAuthenticationResult;
import online.yudream.base.domain.system.security.valobj.PasskeyRegistrationOptions;
import online.yudream.base.domain.system.security.valobj.PasskeyRegistrationResult;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OAuthPasskeyAppService {

    private static final int CLIENT_SECRET_BYTES = 32;

    private final OAuthClientRegistrationRepo oauthClientRegistrationRepo;
    private final OAuthProviderRegistrationRepo oauthProviderRegistrationRepo;
    private final PasskeyCredentialRepo passkeyCredentialRepo;
    private final ApiSecurityPolicyRepo apiSecurityPolicyRepo;
    private final UserRepo userRepo;
    private final PasskeyCeremonyGateway passkeyCeremonyGateway;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<OAuthClientDTO> listClients() {
        ensureOAuthServerEnabled();
        return oauthClientRegistrationRepo.findAll().stream().map(ApiSecurityAssembler::toDTO).toList();
    }

    @Transactional
    public OAuthClientCreateResultDTO createClient(OAuthClientSaveCmd cmd) {
        ensureOAuthServerEnabled();
        if (oauthClientRegistrationRepo.findByClientId(cmd.getClientId()).isPresent()) {
            throw new BizException("OAuth 客户端 ID 已存在");
        }
        String secret = generateSecret();
        OAuthClientRegistration registration = OAuthClientRegistration.create(
                cmd.getClientId(),
                cmd.getClientName(),
                ApiKeySecretHasher.hash(secret)
        );
        applyClientUpdate(registration, cmd);
        return OAuthClientCreateResultDTO.builder()
                .client(ApiSecurityAssembler.toDTO(oauthClientRegistrationRepo.save(registration)))
                .clientSecret(secret)
                .build();
    }

    @Transactional
    public OAuthClientDTO updateClient(OAuthClientSaveCmd cmd) {
        ensureOAuthServerEnabled();
        OAuthClientRegistration registration = oauthClientRegistrationRepo.findById(cmd.getId())
                .orElseThrow(() -> new BizException("OAuth 客户端不存在"));
        applyClientUpdate(registration, cmd);
        return ApiSecurityAssembler.toDTO(oauthClientRegistrationRepo.save(registration));
    }

    @Transactional
    public void disableClient(Long id) {
        ensureOAuthServerEnabled();
        OAuthClientRegistration registration = oauthClientRegistrationRepo.findById(id)
                .orElseThrow(() -> new BizException("OAuth 客户端不存在"));
        registration.disable();
        oauthClientRegistrationRepo.save(registration);
    }

    @Transactional(readOnly = true)
    public List<OAuthProviderDTO> listProviders() {
        ensureOAuthClientEnabled();
        return oauthProviderRegistrationRepo.findAll().stream().map(ApiSecurityAssembler::toDTO).toList();
    }

    @Transactional
    public OAuthProviderDTO saveProvider(OAuthProviderSaveCmd cmd) {
        ensureOAuthClientEnabled();
        OAuthProviderRegistration registration = cmd.getId() == null
                ? createProvider(cmd)
                : oauthProviderRegistrationRepo.findById(cmd.getId()).orElseThrow(() -> new BizException("OAuth 提供商不存在"));
        registration.update(
                cmd.getName(),
                cmd.getIssuerUri(),
                cmd.getAuthorizationUri(),
                cmd.getTokenUri(),
                cmd.getUserInfoUri(),
                cmd.getClientId(),
                cmd.getClientSecret(),
                cmd.getAuthMethod(),
                cmd.getScopes(),
                cmd.getRedirectUri(),
                cmd.getStatus()
        );
        return ApiSecurityAssembler.toDTO(oauthProviderRegistrationRepo.save(registration));
    }

    @Transactional
    public void disableProvider(Long id) {
        ensureOAuthClientEnabled();
        OAuthProviderRegistration registration = oauthProviderRegistrationRepo.findById(id)
                .orElseThrow(() -> new BizException("OAuth 提供商不存在"));
        registration.disable();
        oauthProviderRegistrationRepo.save(registration);
    }

    @Transactional(readOnly = true)
    public List<PasskeyCredentialDTO> listPasskeys(PasskeyCredentialQuery query) {
        ensurePasskeyEnabled();
        Long userId = query == null ? null : query.getUserId();
        List<PasskeyCredential> credentials = userId == null
                ? passkeyCredentialRepo.findAll()
                : passkeyCredentialRepo.findByUserId(userId);
        return credentials.stream().map(ApiSecurityAssembler::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public PasskeyRegistrationOptionsDTO startPasskeyRegistration(PasskeyRegistrationStartCmd cmd) {
        ensurePasskeyEnabled();
        User user = userRepo.findById(cmd.getUserId()).orElseThrow(() -> new BizException("用户不存在"));
        PasskeyRegistrationOptions options = passkeyCeremonyGateway.startRegistration(
                user.getId(),
                user.getUsername(),
                user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname(),
                passkeyCredentialRepo.findByUserId(user.getId())
        );
        return PasskeyRegistrationOptionsDTO.builder()
                .requestJson(options.requestJson())
                .publicKeyJson(options.publicKeyJson())
                .build();
    }

    @Transactional
    public PasskeyCredentialDTO finishPasskeyRegistration(PasskeyRegistrationFinishCmd cmd) {
        ensurePasskeyEnabled();
        User user = userRepo.findById(cmd.getUserId()).orElseThrow(() -> new BizException("用户不存在"));
        PasskeyRegistrationResult result = passkeyCeremonyGateway.finishRegistration(cmd.getRequestJson(), cmd.getResponseJson());
        if (passkeyCredentialRepo.findByCredentialId(result.credentialId()).isPresent()) {
            throw new BizException("Passkey 凭据已存在");
        }
        PasskeyCredential credential = PasskeyCredential.create(user.getId(), result.credentialId(), result.publicKey(), cmd.getDeviceName());
        credential.markUsed(result.signCount(), null);
        return ApiSecurityAssembler.toDTO(passkeyCredentialRepo.save(credential));
    }

    @Transactional(readOnly = true)
    public PasskeyAuthenticationOptionsDTO startPasskeyAuthentication(PasskeyAuthenticationStartCmd cmd) {
        ensurePasskeyEnabled();
        User user = loginUser(cmd.getUsername());
        boolean hasActivePasskey = passkeyCredentialRepo.findByUserId(user.getId()).stream()
                .anyMatch(credential -> credential.getStatus() == CredentialStatus.ACTIVE);
        if (!hasActivePasskey) {
            throw new BizException("当前账号未绑定可用 Passkey");
        }
        PasskeyAuthenticationOptions options = passkeyCeremonyGateway.startAuthentication(user.getUsername());
        return PasskeyAuthenticationOptionsDTO.builder()
                .requestJson(options.requestJson())
                .publicKeyJson(options.publicKeyJson())
                .build();
    }

    @Transactional
    public User finishPasskeyAuthentication(PasskeyAuthenticationFinishCmd cmd) {
        ensurePasskeyEnabled();
        PasskeyAuthenticationResult result = passkeyCeremonyGateway.finishAuthentication(cmd.getRequestJson(), cmd.getResponseJson());
        User user = loginUser(result.username());
        if (!user.getId().equals(result.userId()) || !user.getUsername().equals(cmd.getUsername())) {
            throw new BizException("Passkey 登录用户不匹配");
        }
        PasskeyCredential credential = passkeyCredentialRepo.findByCredentialId(result.credentialId())
                .orElseThrow(() -> new BizException("Passkey 凭据不存在"));
        if (!credential.getUserId().equals(user.getId()) || credential.getStatus() != CredentialStatus.ACTIVE) {
            throw new BizException("Passkey 凭据不可用");
        }
        credential.markUsed(result.signCount(), null);
        passkeyCredentialRepo.save(credential);
        return user;
    }

    @Transactional
    public PasskeyCredentialDTO revokePasskey(PasskeyRevokeCmd cmd) {
        ensurePasskeyEnabled();
        PasskeyCredential credential = passkeyCredentialRepo.findById(cmd.getId())
                .orElseThrow(() -> new BizException("Passkey 凭据不存在"));
        credential.revoke();
        return ApiSecurityAssembler.toDTO(passkeyCredentialRepo.save(credential));
    }

    @Transactional
    public PasskeyCredentialDTO revokeOwnPasskey(PasskeySelfRevokeCmd cmd) {
        ensurePasskeyEnabled();
        PasskeyCredential credential = passkeyCredentialRepo.findById(cmd.getId())
                .orElseThrow(() -> new BizException("Passkey 凭据不存在"));
        if (!credential.getUserId().equals(cmd.getUserId())) {
            throw new BizException("无权吊销该 Passkey 凭据");
        }
        credential.revoke();
        return ApiSecurityAssembler.toDTO(passkeyCredentialRepo.save(credential));
    }

    private OAuthProviderRegistration createProvider(OAuthProviderSaveCmd cmd) {
        if (oauthProviderRegistrationRepo.findByCode(cmd.getCode()).isPresent()) {
            throw new BizException("OAuth 提供商编码已存在");
        }
        return OAuthProviderRegistration.create(cmd.getCode(), cmd.getName());
    }

    private User loginUser(String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BizException("用户不存在或未绑定 Passkey"));
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BizException("用户已停用");
        }
        if (!user.isEmailVerified()) {
            throw new BizException("邮箱未验证，请先验证邮箱");
        }
        return user;
    }

    private void ensurePasskeyEnabled() {
        ApiSecurityPolicy policy = apiSecurityPolicyRepo.findDefault().orElse(ApiSecurityPolicy.createDefault());
        if (!policy.isPasskeyEnabled()) {
            throw new BizException("Passkey 未启用");
        }
    }

    private void ensureOAuthServerEnabled() {
        ApiSecurityPolicy policy = apiSecurityPolicyRepo.findDefault().orElse(ApiSecurityPolicy.createDefault());
        if (!policy.isOauthServerEnabled()) {
            throw new BizException("OAuth 服务端未启用");
        }
    }

    private void ensureOAuthClientEnabled() {
        ApiSecurityPolicy policy = apiSecurityPolicyRepo.findDefault().orElse(ApiSecurityPolicy.createDefault());
        if (!policy.isOauthClientEnabled()) {
            throw new BizException("OAuth 客户端未启用");
        }
    }

    private void applyClientUpdate(OAuthClientRegistration registration, OAuthClientSaveCmd cmd) {
        registration.update(
                cmd.getClientName(),
                cmd.getAuthMethod(),
                cmd.getGrantTypes(),
                cmd.getRedirectUris(),
                cmd.getScopes(),
                cmd.getAccessTokenTtlSeconds(),
                cmd.getRefreshTokenTtlSeconds(),
                cmd.getStatus()
        );
    }

    private String generateSecret() {
        byte[] bytes = new byte[CLIENT_SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return "ydc_" + HexFormat.of().formatHex(bytes);
    }
}
