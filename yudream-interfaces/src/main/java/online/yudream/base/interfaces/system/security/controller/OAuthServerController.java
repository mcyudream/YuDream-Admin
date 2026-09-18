package online.yudream.base.interfaces.system.security.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.dto.LoginTokenDTO;
import online.yudream.base.application.system.security.dto.OAuthAuthorizationDTO;
import online.yudream.base.application.system.security.service.LoginTokenAppService;
import online.yudream.base.application.system.security.service.OAuthServerAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import online.yudream.base.interfaces.system.security.assembler.ApiSecurityWebAssembler;
import online.yudream.base.interfaces.system.security.request.OAuthAuthorizeRequest;
import online.yudream.base.interfaces.system.security.request.OAuthTokenRequest;
import online.yudream.base.interfaces.system.security.res.OAuthAuthorizationRes;
import online.yudream.base.interfaces.system.security.res.OAuthTokenRes;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
public class OAuthServerController {

    private final OAuthServerAppService oauthServerAppService;
    private final LoginTokenAppService loginTokenAppService;

    @GetMapping("/authorize")
    public Result<OAuthAuthorizationRes> authorize(@ModelAttribute OAuthAuthorizeRequest request) {
        StpUtil.checkLogin();
        OAuthAuthorizationDTO authorization = oauthServerAppService.authorize(
                ApiSecurityWebAssembler.toCmd(request, StpUtil.getLoginIdAsLong()));
        return Result.ok(ApiSecurityWebAssembler.toRes(authorization));
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public OAuthTokenRes token(@ModelAttribute OAuthTokenRequest request,
                               @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return ApiSecurityWebAssembler.toRes(oauthServerAppService.token(ApiSecurityWebAssembler.toCmd(request, authorizationHeader)));
    }

    /**
     * OAuth 访问令牌换取登录（Sa-Token）会话：启动器等公开客户端完成授权码
     * 流程后，用 access_token 换发一枚真正的登录会话令牌，后续域请求即以
     * 完整 RBAC 的真实用户身份访问（而非 scope 镜像权限）。匿名或令牌无效
     * 时由 current() 抛未认证。
     */
    @PostMapping("/satoken/exchange")
    public Result<Map<String, Object>> exchangeSatoken() {
        SecurityPrincipalSupport.SecurityPrincipal principal = SecurityPrincipalSupport.current();
        LoginTokenDTO token = loginTokenAppService.issueForLogin(principal.userId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token.getToken());
        payload.put("tokenName", token.getTokenName());
        payload.put("refreshToken", token.getRefreshToken());
        payload.put("expiresIn", token.getExpiresIn());
        return Result.ok(payload);
    }
}
