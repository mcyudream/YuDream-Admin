package online.yudream.base.interfaces.system.security.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.service.OAuthClientAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.security.assembler.ApiSecurityWebAssembler;
import online.yudream.base.interfaces.system.security.request.OAuthClientAuthorizeRequest;
import online.yudream.base.interfaces.system.security.request.OAuthClientCallbackRequest;
import online.yudream.base.interfaces.system.security.res.OAuthClientAuthorizeRes;
import online.yudream.base.interfaces.system.security.res.OAuthClientCallbackRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/oauth/client")
@RequiredArgsConstructor
public class OAuthClientController {

    private final OAuthClientAppService oauthClientAppService;

    @GetMapping("/{providerCode}/authorize")
    public Result<OAuthClientAuthorizeRes> authorize(@PathVariable String providerCode,
                                                     @ModelAttribute OAuthClientAuthorizeRequest request) {
        return Result.ok(ApiSecurityWebAssembler.toRes(
                oauthClientAppService.authorize(ApiSecurityWebAssembler.toCmd(providerCode, request))));
    }

    @GetMapping("/{providerCode}/callback")
    public Result<OAuthClientCallbackRes> callback(@PathVariable String providerCode,
                                                   @ModelAttribute OAuthClientCallbackRequest request) {
        return Result.ok(ApiSecurityWebAssembler.toRes(
                oauthClientAppService.callback(ApiSecurityWebAssembler.toCmd(providerCode, request))));
    }
}
