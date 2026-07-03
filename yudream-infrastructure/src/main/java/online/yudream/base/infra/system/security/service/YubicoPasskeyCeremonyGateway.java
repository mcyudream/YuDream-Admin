package online.yudream.base.infra.system.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.exception.Base64UrlException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.PasskeyCredential;
import online.yudream.base.domain.system.security.enumerate.CredentialStatus;
import online.yudream.base.domain.system.security.repo.PasskeyCredentialRepo;
import online.yudream.base.domain.system.security.service.PasskeyCeremonyGateway;
import online.yudream.base.domain.system.security.valobj.PasskeyRegistrationOptions;
import online.yudream.base.domain.system.security.valobj.PasskeyRegistrationResult;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.UserRepo;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class YubicoPasskeyCeremonyGateway implements PasskeyCeremonyGateway {

    private static final String RP_ID = "localhost";
    private static final String RP_NAME = "YuDream Admin";
    private static final Set<String> ORIGINS = Set.of(
            "http://localhost:9002",
            "http://localhost:8080",
            "http://127.0.0.1:9002"
    );

    private final UserRepo userRepo;
    private final PasskeyCredentialRepo passkeyCredentialRepo;

    @Override
    public PasskeyRegistrationOptions startRegistration(Long userId, String username, String displayName, List<PasskeyCredential> existingCredentials) {
        try {
            PublicKeyCredentialCreationOptions request = relyingParty().startRegistration(StartRegistrationOptions.builder()
                    .user(UserIdentity.builder()
                            .name(username)
                            .displayName(displayName)
                            .id(userHandle(userId))
                            .build())
                    .build());
            return new PasskeyRegistrationOptions(request.toJson(), request.toCredentialsCreateJson());
        }
        catch (JsonProcessingException e) {
            throw new BizException("Passkey 注册参数生成失败");
        }
    }

    @Override
    public PasskeyRegistrationResult finishRegistration(String requestJson, String responseJson) {
        try {
            PublicKeyCredentialCreationOptions request = PublicKeyCredentialCreationOptions.fromJson(requestJson);
            RegistrationResult result = relyingParty().finishRegistration(FinishRegistrationOptions.builder()
                    .request(request)
                    .response(PublicKeyCredential.parseRegistrationResponseJson(responseJson))
                    .build());
            return new PasskeyRegistrationResult(
                    result.getKeyId().getId().getBase64Url(),
                    result.getPublicKeyCose().getBase64Url(),
                    result.getSignatureCount()
            );
        }
        catch (JsonProcessingException e) {
            throw new BizException("Passkey 注册请求参数无效");
        }
        catch (IOException e) {
            throw new BizException("Passkey 浏览器响应解析失败");
        }
        catch (RegistrationFailedException e) {
            throw new BizException("Passkey 注册校验失败：" + e.getMessage());
        }
    }

    private RelyingParty relyingParty() {
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder().id(RP_ID).name(RP_NAME).build())
                .credentialRepository(new Repository())
                .origins(ORIGINS)
                .allowUntrustedAttestation(true)
                .build();
    }

    private ByteArray userHandle(Long userId) {
        return new ByteArray(String.valueOf(userId).getBytes(StandardCharsets.UTF_8));
    }

    private Optional<Long> parseUserHandle(ByteArray userHandle) {
        try {
            return Optional.of(Long.parseLong(new String(userHandle.getBytes(), StandardCharsets.UTF_8)));
        }
        catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<RegisteredCredential> toRegisteredCredential(PasskeyCredential credential) {
        if (credential == null || credential.getStatus() != CredentialStatus.ACTIVE) {
            return Optional.empty();
        }
        try {
            return Optional.of(RegisteredCredential.builder()
                    .credentialId(ByteArray.fromBase64Url(credential.getCredentialId()))
                    .userHandle(userHandle(credential.getUserId()))
                    .publicKeyCose(ByteArray.fromBase64Url(credential.getPublicKey()))
                    .signatureCount(credential.getSignCount())
                    .build());
        }
        catch (Base64UrlException e) {
            return Optional.empty();
        }
    }

    private Optional<PublicKeyCredentialDescriptor> toDescriptor(PasskeyCredential credential) {
        if (credential == null || credential.getStatus() != CredentialStatus.ACTIVE) {
            return Optional.empty();
        }
        try {
            return Optional.of(PublicKeyCredentialDescriptor.builder()
                    .id(ByteArray.fromBase64Url(credential.getCredentialId()))
                    .build());
        }
        catch (Base64UrlException e) {
            return Optional.empty();
        }
    }

    private class Repository implements CredentialRepository {

        @Override
        public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
            return userRepo.findByUsername(username)
                    .map(User::getId)
                    .stream()
                    .flatMap(userId -> passkeyCredentialRepo.findByUserId(userId).stream())
                    .flatMap(credential -> toDescriptor(credential).stream())
                    .collect(Collectors.toSet());
        }

        @Override
        public Optional<ByteArray> getUserHandleForUsername(String username) {
            return userRepo.findByUsername(username).map(User::getId).map(YubicoPasskeyCeremonyGateway.this::userHandle);
        }

        @Override
        public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
            return parseUserHandle(userHandle).flatMap(userRepo::findById).map(User::getUsername);
        }

        @Override
        public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
            return passkeyCredentialRepo.findByCredentialId(credentialId.getBase64Url())
                    .filter(credential -> parseUserHandle(userHandle).filter(id -> id.equals(credential.getUserId())).isPresent())
                    .flatMap(YubicoPasskeyCeremonyGateway.this::toRegisteredCredential);
        }

        @Override
        public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
            return passkeyCredentialRepo.findByCredentialId(credentialId.getBase64Url())
                    .flatMap(YubicoPasskeyCeremonyGateway.this::toRegisteredCredential)
                    .stream()
                    .collect(Collectors.toSet());
        }
    }
}
