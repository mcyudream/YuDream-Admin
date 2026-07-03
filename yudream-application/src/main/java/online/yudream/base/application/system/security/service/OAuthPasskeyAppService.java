package online.yudream.base.application.system.security.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.assembler.ApiSecurityAssembler;
import online.yudream.base.application.system.security.cmd.OAuthClientSaveCmd;
import online.yudream.base.application.system.security.cmd.OAuthProviderSaveCmd;
import online.yudream.base.application.system.security.cmd.PasskeyRevokeCmd;
import online.yudream.base.application.system.security.dto.OAuthClientCreateResultDTO;
import online.yudream.base.application.system.security.dto.OAuthClientDTO;
import online.yudream.base.application.system.security.dto.OAuthProviderDTO;
import online.yudream.base.application.system.security.dto.PasskeyCredentialDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.OAuthClientRegistration;
import online.yudream.base.domain.system.security.aggregate.OAuthProviderRegistration;
import online.yudream.base.domain.system.security.aggregate.PasskeyCredential;
import online.yudream.base.domain.system.security.repo.OAuthClientRegistrationRepo;
import online.yudream.base.domain.system.security.repo.OAuthProviderRegistrationRepo;
import online.yudream.base.domain.system.security.repo.PasskeyCredentialRepo;
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
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<OAuthClientDTO> listClients() {
        return oauthClientRegistrationRepo.findAll().stream().map(ApiSecurityAssembler::toDTO).toList();
    }

    @Transactional
    public OAuthClientCreateResultDTO createClient(OAuthClientSaveCmd cmd) {
        if (oauthClientRegistrationRepo.findByClientId(cmd.getClientId()).isPresent()) {
            throw new BizException("OAuth 客户端ID已存在");
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
        OAuthClientRegistration registration = oauthClientRegistrationRepo.findById(cmd.getId())
                .orElseThrow(() -> new BizException("OAuth 客户端不存在"));
        applyClientUpdate(registration, cmd);
        return ApiSecurityAssembler.toDTO(oauthClientRegistrationRepo.save(registration));
    }

    @Transactional
    public void disableClient(Long id) {
        OAuthClientRegistration registration = oauthClientRegistrationRepo.findById(id)
                .orElseThrow(() -> new BizException("OAuth 客户端不存在"));
        registration.disable();
        oauthClientRegistrationRepo.save(registration);
    }

    @Transactional(readOnly = true)
    public List<OAuthProviderDTO> listProviders() {
        return oauthProviderRegistrationRepo.findAll().stream().map(ApiSecurityAssembler::toDTO).toList();
    }

    @Transactional
    public OAuthProviderDTO saveProvider(OAuthProviderSaveCmd cmd) {
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
        OAuthProviderRegistration registration = oauthProviderRegistrationRepo.findById(id)
                .orElseThrow(() -> new BizException("OAuth 提供商不存在"));
        registration.disable();
        oauthProviderRegistrationRepo.save(registration);
    }

    @Transactional(readOnly = true)
    public List<PasskeyCredentialDTO> listPasskeys(Long userId) {
        return passkeyCredentialRepo.findByUserId(userId).stream().map(ApiSecurityAssembler::toDTO).toList();
    }

    @Transactional
    public PasskeyCredentialDTO revokePasskey(PasskeyRevokeCmd cmd) {
        PasskeyCredential credential = passkeyCredentialRepo.findById(cmd.getId())
                .orElseThrow(() -> new BizException("Passkey 凭据不存在"));
        credential.revoke();
        return ApiSecurityAssembler.toDTO(passkeyCredentialRepo.save(credential));
    }

    private OAuthProviderRegistration createProvider(OAuthProviderSaveCmd cmd) {
        if (oauthProviderRegistrationRepo.findByCode(cmd.getCode()).isPresent()) {
            throw new BizException("OAuth 提供商编码已存在");
        }
        return OAuthProviderRegistration.create(cmd.getCode(), cmd.getName());
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
