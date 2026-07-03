package online.yudream.base.interfaces.system.security.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.dto.OAuthAuthorizationDTO;
import online.yudream.base.application.system.security.service.OAuthServerAppService;
import online.yudream.base.interfaces.system.security.assembler.ApiSecurityWebAssembler;
import online.yudream.base.interfaces.system.security.request.OAuthAuthorizeRequest;
import online.yudream.base.interfaces.system.security.request.OAuthTokenRequest;
import online.yudream.base.interfaces.system.security.res.OAuthTokenRes;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
public class OAuthServerController {

    private final OAuthServerAppService oauthServerAppService;

    @GetMapping("/authorize")
    public RedirectView authorize(@ModelAttribute OAuthAuthorizeRequest request) {
        OAuthAuthorizationDTO authorization = oauthServerAppService.authorize(
                ApiSecurityWebAssembler.toCmd(request, StpUtil.getLoginIdAsLong()));
        return ApiSecurityWebAssembler.toRedirectView(authorization);
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public OAuthTokenRes token(@ModelAttribute OAuthTokenRequest request,
                               @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return ApiSecurityWebAssembler.toRes(oauthServerAppService.token(ApiSecurityWebAssembler.toCmd(request, authorizationHeader)));
    }
}
