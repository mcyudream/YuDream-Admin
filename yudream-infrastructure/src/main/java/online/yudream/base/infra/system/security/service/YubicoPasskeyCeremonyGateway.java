package online.yudream.base.infra.system.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.exception.Base64UrlException;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.PasskeyCredential;
import online.yudream.base.domain.system.security.enumerate.CredentialStatus;
import online.yudream.base.domain.system.security.repo.PasskeyCredentialRepo;
import online.yudream.base.domain.system.security.service.PasskeyCeremonyGateway;
import online.yudream.base.domain.system.security.valobj.PasskeyAuthenticationOptions;
import online.yudream.base.domain.system.security.valobj.PasskeyAuthenticationResult;
import online.yudream.base.domain.system.security.valobj.PasskeyRegistrationOptions;
import online.yudream.base.domain.system.security.valobj.PasskeyRegistrationResult;
import online.yudream.base.domain.system.security.valobj.PasskeyRelyingPartyContext;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.UserRepo;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class YubicoPasskeyCeremonyGateway implements PasskeyCeremonyGateway {

    private final UserRepo userRepo;
    private final PasskeyCredentialRepo passkeyCredentialRepo;

    /**
     * 服务端 challenge 绑定：start 阶段签发的 challenge 必须在服务端登记，
     * finish 阶段按 challenge 一次性核销；客户端自带的 requestJson 仅作为载体，
     * challenge 未登记、已过期或已使用即拒绝，断言无法重放。
     */
    private static final Duration CEREMONY_TTL = Duration.ofMinutes(5);
    private static final int MAX_PENDING_CHALLENGES = 4096;
    private final Map<String, Instant> pendingChallenges = new ConcurrentHashMap<>();

    private void rememberChallenge(ByteArray challenge) {
        if (pendingChallenges.size() >= MAX_PENDING_CHALLENGES) {
            Instant now = Instant.now();
            pendingChallenges.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        }
        pendingChallenges.put(challengeKey(challenge), Instant.now().plus(CEREMONY_TTL));
    }

    private void consumeChallenge(ByteArray challenge) {
        Instant expireAt = pendingChallenges.remove(challengeKey(challenge));
        if (expireAt == null || expireAt.isBefore(Instant.now())) {
            throw new BizException("Passkey 校验请求无效或已过期，请重新发起");
        }
    }

    private String challengeKey(ByteArray challenge) {
        return Base64.getEncoder().encodeToString(challenge.getBytes());
    }

    @Override
    public PasskeyRegistrationOptions startRegistration(
            PasskeyRelyingPartyContext relyingParty,
            Long userId,
            String username,
            String displayName,
            List<PasskeyCredential> existingCredentials
    ) {
        try {
            PublicKeyCredentialCreationOptions request = relyingParty(relyingParty).startRegistration(StartRegistrationOptions.builder()
                    .user(UserIdentity.builder()
                            .name(username)
                            .displayName(displayName)
                            .id(userHandle(userId))
                            .build())
                    .build());
            rememberChallenge(request.getChallenge());
            return new PasskeyRegistrationOptions(request.toJson(), request.toCredentialsCreateJson());
        }
        catch (JsonProcessingException e) {
            throw new BizException("Passkey 注册参数生成失败");
        }
    }

    @Override
    public PasskeyRegistrationResult finishRegistration(PasskeyRelyingPartyContext relyingParty, String requestJson, String responseJson) {
        try {
            PublicKeyCredentialCreationOptions request = PublicKeyCredentialCreationOptions.fromJson(requestJson);
            consumeChallenge(request.getChallenge());
            RegistrationResult result = relyingParty(relyingParty).finishRegistration(FinishRegistrationOptions.builder()
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

    @Override
    public PasskeyAuthenticationOptions startAuthentication(PasskeyRelyingPartyContext relyingParty, String username) {
        try {
            AssertionRequest request = relyingParty(relyingParty).startAssertion(StartAssertionOptions.builder()
                    .username(username)
                    .build());
            rememberChallenge(request.getPublicKeyCredentialRequestOptions().getChallenge());
            return new PasskeyAuthenticationOptions(request.toJson(), request.toCredentialsGetJson());
        }
        catch (JsonProcessingException e) {
            throw new BizException("Passkey 登录参数生成失败");
        }
    }

    @Override
    public PasskeyAuthenticationResult finishAuthentication(PasskeyRelyingPartyContext relyingParty, String requestJson, String responseJson) {
        try {
            AssertionRequest request = AssertionRequest.fromJson(requestJson);
            consumeChallenge(request.getPublicKeyCredentialRequestOptions().getChallenge());
            AssertionResult result = relyingParty(relyingParty).finishAssertion(FinishAssertionOptions.builder()
                    .request(request)
                    .response(PublicKeyCredential.parseAssertionResponseJson(responseJson))
                    .build());
            if (!result.isSuccess()) {
                throw new BizException("Passkey 登录校验失败");
            }
            Long userId = parseUserHandle(result.getUserHandle())
                    .orElseThrow(() -> new BizException("Passkey 用户句柄无效"));
            return new PasskeyAuthenticationResult(
                    userId,
                    result.getUsername(),
                    result.getCredentialId().getBase64Url(),
                    result.getSignatureCount()
            );
        }
        catch (JsonProcessingException e) {
            throw new BizException("Passkey 登录请求参数无效");
        }
        catch (IOException e) {
            throw new BizException("Passkey 浏览器响应解析失败");
        }
        catch (AssertionFailedException e) {
            throw new BizException("Passkey 登录校验失败：" + e.getMessage());
        }
    }

    private RelyingParty relyingParty(PasskeyRelyingPartyContext relyingParty) {
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder().id(relyingParty.rpId()).name(relyingParty.rpName()).build())
                .credentialRepository(new Repository())
                .origins(Set.of(relyingParty.origin()))
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
