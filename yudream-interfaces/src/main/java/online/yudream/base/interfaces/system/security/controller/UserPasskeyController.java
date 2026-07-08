package online.yudream.base.interfaces.system.security.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.service.OAuthPasskeyAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.security.assembler.ApiSecurityWebAssembler;
import online.yudream.base.interfaces.system.security.assembler.PasskeyWebAssembler;
import online.yudream.base.interfaces.system.security.request.PasskeyRegistrationFinishRequest;
import online.yudream.base.interfaces.system.security.res.PasskeyCredentialRes;
import online.yudream.base.interfaces.system.security.res.PasskeyRegistrationOptionsRes;
import online.yudream.base.interfaces.system.security.support.PasskeyRelyingPartySupport;
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
        return Result.ok(ApiSecurityWebAssembler.toPasskeyResList(
                oauthPasskeyAppService.listPasskeys(ApiSecurityWebAssembler.toPasskeyCredentialQuery(userId))
        ));
    }

    @PostMapping("/registration/options")
    public Result<PasskeyRegistrationOptionsRes> startRegistration(HttpServletRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(ApiSecurityWebAssembler.toRes(oauthPasskeyAppService.startPasskeyRegistration(
                PasskeyWebAssembler.toRegistrationStartCmd(userId, PasskeyRelyingPartySupport.from(request)))));
    }

    @PostMapping("/registration")
    public Result<PasskeyCredentialRes> finishRegistration(
            @Valid @RequestBody PasskeyRegistrationFinishRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(ApiSecurityWebAssembler.toRes(oauthPasskeyAppService.finishPasskeyRegistration(
                PasskeyWebAssembler.toRegistrationFinishCmd(userId, request, PasskeyRelyingPartySupport.from(httpRequest)))));
    }

    @PostMapping("/{id}/revoke")
    public Result<PasskeyCredentialRes> revokeOwnPasskey(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(ApiSecurityWebAssembler.toRes(
                oauthPasskeyAppService.revokeOwnPasskey(ApiSecurityWebAssembler.toPasskeySelfRevokeCmd(id, userId))));
    }
}
