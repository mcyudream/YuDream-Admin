package online.yudream.base.interfaces.system.security.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.service.OAuthPasskeyAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.security.assembler.ApiSecurityWebAssembler;
import online.yudream.base.interfaces.system.security.request.PasskeyRegistrationFinishRequest;
import online.yudream.base.interfaces.system.security.res.PasskeyCredentialRes;
import online.yudream.base.interfaces.system.security.res.PasskeyRegistrationOptionsRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user/me/passkeys")
@RequiredArgsConstructor
public class UserPasskeyController {

    private final OAuthPasskeyAppService oauthPasskeyAppService;

    @GetMapping
    public Result<List<PasskeyCredentialRes>> listOwnPasskeys() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(oauthPasskeyAppService.listPasskeys(userId).stream().map(ApiSecurityWebAssembler::toRes).toList());
    }

    @PostMapping("/registration/options")
    public Result<PasskeyRegistrationOptionsRes> startRegistration() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(ApiSecurityWebAssembler.toRes(oauthPasskeyAppService.startPasskeyRegistration(
                ApiSecurityWebAssembler.toPasskeyRegistrationStartCmd(userId))));
    }

    @PostMapping("/registration")
    public Result<PasskeyCredentialRes> finishRegistration(@Valid @RequestBody PasskeyRegistrationFinishRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(ApiSecurityWebAssembler.toRes(oauthPasskeyAppService.finishPasskeyRegistration(
                ApiSecurityWebAssembler.toPasskeyRegistrationFinishCmd(userId, request))));
    }

    @PostMapping("/{id}/revoke")
    public Result<PasskeyCredentialRes> revokeOwnPasskey(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(ApiSecurityWebAssembler.toRes(
                oauthPasskeyAppService.revokeOwnPasskey(ApiSecurityWebAssembler.toPasskeySelfRevokeCmd(id, userId))));
    }
}
