package online.yudream.base.interfaces.system.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.monitor.service.SystemMonitorAppService;
import online.yudream.base.application.system.security.dto.LoginTokenDTO;
import online.yudream.base.application.system.security.service.LoginTokenAppService;
import online.yudream.base.application.system.security.service.OAuthPasskeyAppService;
import online.yudream.base.application.system.user.service.PermissionAppService;
import online.yudream.base.application.system.user.service.UserAppService;
import online.yudream.base.application.system.user.service.UserContextAppService;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.security.assembler.PasskeyWebAssembler;
import online.yudream.base.interfaces.system.security.support.PasskeyRelyingPartySupport;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import online.yudream.base.interfaces.system.user.assembler.UserWebAssembler;
import online.yudream.base.interfaces.system.user.request.PasskeyAuthenticationFinishRequest;
import online.yudream.base.interfaces.system.user.request.PasskeyAuthenticationStartRequest;
import online.yudream.base.interfaces.system.user.request.UserLoginRequest;
import online.yudream.base.interfaces.system.user.request.UserPasswordResetEmailRequest;
import online.yudream.base.interfaces.system.user.request.UserPasswordResetRequest;
import online.yudream.base.interfaces.system.user.request.UserProfileUpdateRequest;
import online.yudream.base.interfaces.system.user.request.UserRegisterRequest;
import online.yudream.base.interfaces.system.user.request.UserSwitchDeptRequest;
import online.yudream.base.interfaces.system.user.request.UserSwitchRoleRequest;
import online.yudream.base.interfaces.system.user.request.UserTokenRefreshRequest;
import online.yudream.base.interfaces.system.user.res.PasskeyAuthenticationOptionsRes;
import online.yudream.base.interfaces.system.user.res.UserLoginRes;
import online.yudream.base.interfaces.system.user.res.UserContextRes;
import online.yudream.base.interfaces.system.user.res.UserDeptRes;
import online.yudream.base.interfaces.system.user.res.UserProfileRes;
import online.yudream.base.interfaces.system.user.res.MessagingBindingCodeRes;
import online.yudream.base.interfaces.system.user.res.MessagingBindingTargetRes;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingCode;
import online.yudream.base.interfaces.system.user.res.UserRegisterRes;
import online.yudream.base.interfaces.system.user.res.UserRoleRes;
import online.yudream.base.interfaces.system.user.res.UserVerificationMethodRes;
import online.yudream.base.interfaces.system.user.vo.PermissionListVO;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserAppService userAppService;
    private final UserContextAppService userContextAppService;
    private final PermissionAppService permissionAppService;
    private final SystemMonitorAppService systemMonitorAppService;
    private final LoginTokenAppService loginTokenAppService;
    private final OAuthPasskeyAppService oauthPasskeyAppService;
    private final online.yudream.base.application.system.setting.service.SettingAppService settingAppService;
    private final online.yudream.base.interfaces.common.support.AuthRateLimiter authRateLimiter;

    /** 匿名可触达认证端点的限流参数（按 IP / 按账号双维度）。 */
    private static final java.time.Duration RATE_WINDOW = java.time.Duration.ofMinutes(1);
    private static final int LOGIN_IP_LIMIT = 20;
    private static final int LOGIN_ACCOUNT_LIMIT = 8;
    private static final int REGISTER_IP_LIMIT = 5;
    private static final int RESET_EMAIL_LIMIT = 3;

    private void limitAuth(String bucket, String key) {
        authRateLimiter.check(bucket, key, switch (bucket) {
            case "login-ip" -> LOGIN_IP_LIMIT;
            case "login-account" -> LOGIN_ACCOUNT_LIMIT;
            case "register-ip" -> REGISTER_IP_LIMIT;
            case "reset-email" -> RESET_EMAIL_LIMIT;
            default -> LOGIN_IP_LIMIT;
        }, RATE_WINDOW);
    }

    @PostMapping("/register")
    public Result<UserRegisterRes> register(@Valid @RequestBody UserRegisterRequest request, HttpServletRequest httpRequest) {
        limitAuth("register-ip", clientIp(httpRequest));
        return Result.ok(UserWebAssembler.toRegisterRes(userAppService.register(UserWebAssembler.toRegisterCmd(request))));
    }

    @GetMapping("/register/verification-methods")
    public Result<List<UserVerificationMethodRes>> registerVerificationMethods() {
        return Result.ok(UserWebAssembler.toVerificationMethodResList(userAppService.availableVerificationMethods()));
    }

    @PostMapping("/password-reset/email")
    public Result<Void> sendPasswordResetEmail(@Valid @RequestBody UserPasswordResetEmailRequest request, HttpServletRequest httpRequest) {
        limitAuth("reset-email", clientIp(httpRequest));
        userAppService.sendPasswordResetEmail(UserWebAssembler.toCmd(request));
        return Result.ok();
    }

    @PostMapping("/password-reset")
    public Result<Void> resetPassword(@Valid @RequestBody UserPasswordResetRequest request) {
        userAppService.resetPassword(UserWebAssembler.toCmd(request));
        return Result.ok();
    }

    @PostMapping("/login")
    public Result<UserLoginRes> login(@Valid @RequestBody UserLoginRequest request, HttpServletRequest httpRequest) {
        limitAuth("login-ip", clientIp(httpRequest));
        limitAuth("login-account", request.getUsername());
        try {
            User user = userAppService.login(UserWebAssembler.toLoginCmd(request), request.getBindingToken());
            LoginTokenDTO token = loginTokenAppService.issueForLogin(user.getId());
            UserLoginRes res = UserWebAssembler.toLoginRes(user, token, userAppService.avatarUrl(user));
            recordLoginLog(request, httpRequest, user, true, "success");
            return Result.ok(res);
        }
        catch (RuntimeException e) {
            recordLoginLog(request, httpRequest, null, false, e.getMessage());
            throw e;
        }
    }

    @PostMapping("/token/refresh")
    public Result<UserLoginRes> refreshToken(@RequestBody UserTokenRefreshRequest request) {
        return Result.ok(UserWebAssembler.toLoginRes(loginTokenAppService.refresh(UserWebAssembler.toCmd(request))));
    }

    @PostMapping("/passkeys/authentication/options")
    public Result<PasskeyAuthenticationOptionsRes> startPasskeyAuthentication(
            @Valid @RequestBody PasskeyAuthenticationStartRequest request,
            HttpServletRequest httpRequest
    ) {
        return Result.ok(UserWebAssembler.toRes(oauthPasskeyAppService.startPasskeyAuthentication(
                PasskeyWebAssembler.toAuthenticationStartCmd(request, PasskeyRelyingPartySupport.from(httpRequest, siteName())))));
    }

    @PostMapping("/passkeys/authentication")
    public Result<UserLoginRes> finishPasskeyAuthentication(@Valid @RequestBody PasskeyAuthenticationFinishRequest request, HttpServletRequest httpRequest) {
        try {
            User user = oauthPasskeyAppService.finishPasskeyAuthentication(
                    PasskeyWebAssembler.toAuthenticationFinishCmd(request, PasskeyRelyingPartySupport.from(httpRequest, siteName())));
            LoginTokenDTO token = loginTokenAppService.issueForLogin(user.getId());
            UserLoginRes res = UserWebAssembler.toLoginRes(user, token, userAppService.avatarUrl(user));
            recordLoginLog(request.getUsername(), httpRequest, user, true, "passkey success");
            return Result.ok(res);
        }
        catch (RuntimeException e) {
            recordLoginLog(request.getUsername(), httpRequest, null, false, e.getMessage());
            throw e;
        }
    }

    private String siteName() {
        try {
            return settingAppService.siteSettings().getSiteName();
        }
        catch (Exception ignored) {
            return null;
        }
    }

    // 登录审计不记录令牌：原始会话令牌属于持有者凭据，落库即泄露面。
    private void recordLoginLog(UserLoginRequest request, HttpServletRequest httpRequest, User user, boolean success, String message) {
        systemMonitorAppService.recordLoginLog(UserWebAssembler.toLoginLogDTO(
                request, user, success, message, clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    private void recordLoginLog(String username, HttpServletRequest httpRequest, User user, boolean success, String message) {
        systemMonitorAppService.recordLoginLog(UserWebAssembler.toLoginLogDTO(
                username, user, success, message, clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    private String clientIp(HttpServletRequest request) {
        // 转发头仅在反向代理后可信：逐段校验为合法 IP 字面量，非法值一律回退直连地址。
        for (String candidate : new String[]{request.getHeader("X-Forwarded-For"), request.getHeader("X-Real-IP")}) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            for (String part : candidate.split(",")) {
                String ip = part.trim();
                if (isValidIpLiteral(ip)) {
                    return ip;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private boolean isValidIpLiteral(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            java.net.InetAddress address = java.net.InetAddress.getByName(value);
            return value.indexOf(':') >= 0 || address.getHostAddress().replace("/", "").equals(value);
        } catch (Exception e) {
            return false;
        }
    }

    @GetMapping("/me/profile")
    public Result<UserProfileRes> profile() {
        return Result.ok(UserWebAssembler.toProfileRes(userAppService.profile(StpUtil.getLoginIdAsLong())));
    }

    @PostMapping("/me/qq-binding-code")
    public Result<PluginQqBindingCode> issueQqBindingCode() {
        return Result.ok(userAppService.issueQqBindingCode(StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/me/messaging-binding-targets")
    public Result<List<MessagingBindingTargetRes>> messagingBindingTargets() {
        return Result.ok(UserWebAssembler.toBindingTargetResList(
                userAppService.listMessagingBindingTargets(StpUtil.getLoginIdAsLong())));
    }

    @PostMapping("/me/messaging-binding-codes")
    public Result<MessagingBindingCodeRes> issueMessagingBindingCode(@RequestParam String connectionId) {
        return Result.ok(UserWebAssembler.toBindingCodeRes(
                userAppService.issueMessagingBindingCode(StpUtil.getLoginIdAsLong(), connectionId)));
    }

    @PostMapping("/me/resend-verification-email")
    public Result<Void> resendVerificationEmail() {
        userAppService.resendVerificationEmail(StpUtil.getLoginIdAsLong());
        return Result.ok();
    }

    @PutMapping("/me/profile")
    public Result<UserProfileRes> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        return Result.ok(UserWebAssembler.toProfileRes(
                userAppService.updateProfile(StpUtil.getLoginIdAsLong(), UserWebAssembler.toProfileUpdateCmd(request))));
    }

    @PostMapping("/me/avatar")
    public Result<UserProfileRes> updateAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(UserWebAssembler.toProfileRes(userAppService.updateAvatar(
                StpUtil.getLoginIdAsLong(),
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize())));
    }

    @GetMapping("/verify-email")
    public Result<Void> verifyEmail(@RequestParam String token) {
        userAppService.verifyEmail(token);
        return Result.ok();
    }

    @GetMapping("/me/depts")
    public Result<List<UserDeptRes>> listMyDepts() {
        return Result.ok(UserWebAssembler.toDeptResList(userContextAppService.listDepts(currentUserId())));
    }

    @GetMapping("/me/roles")
    public Result<List<UserRoleRes>> listMyRoles() {
        return Result.ok(UserWebAssembler.toRoleResList(userContextAppService.listRoles(currentUserId())));
    }

    @GetMapping("/me/context")
    public Result<UserContextRes> getMyContext() {
        return Result.ok(UserWebAssembler.toContextRes(userContextAppService.getContext(currentUserId())));
    }

    @PostMapping("/me/switch-dept")
    public Result<Void> switchDept(@Valid @RequestBody UserSwitchDeptRequest request) {
        userContextAppService.switchDept(currentUserId(), request.getDeptId());
        return Result.ok();
    }

    @PostMapping("/me/switch-role")
    public Result<Void> switchRole(@Valid @RequestBody UserSwitchRoleRequest request) {
        userContextAppService.switchRole(currentUserId(), request.getRoleId());
        return Result.ok();
    }

    @GetMapping("/permissions")
    public Result<PermissionListVO> permissions() {
        Long userId = currentUserId();
        return Result.ok(UserWebAssembler.toPermissionListVO(
                permissionAppService.getUserPermissions(userId),
                userAppService.isEmailVerified(userId)));
    }

    /**
     * 浏览器会话走 Sa-Token；YMCL 等公开客户端走 OAuth Bearer。
     * 两者都只能操作令牌所属用户自己的部门/角色，不能冒充他人。
     */
    private Long currentUserId() {
        return SecurityPrincipalSupport.currentOrOAuth().userId();
    }
}
