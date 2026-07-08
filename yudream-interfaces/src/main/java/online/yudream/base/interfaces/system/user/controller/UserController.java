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
import online.yudream.base.interfaces.system.user.res.UserRegisterRes;
import online.yudream.base.interfaces.system.user.res.UserRoleRes;
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

    @PostMapping("/register")
    public Result<UserRegisterRes> register(@Valid @RequestBody UserRegisterRequest request) {
        return Result.ok(UserWebAssembler.toRegisterRes(userAppService.register(UserWebAssembler.toRegisterCmd(request))));
    }

    @PostMapping("/password-reset/email")
    public Result<Void> sendPasswordResetEmail(@Valid @RequestBody UserPasswordResetEmailRequest request) {
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
        try {
            User user = userAppService.login(UserWebAssembler.toLoginCmd(request));
            LoginTokenDTO token = loginTokenAppService.issueForLogin(user.getId());
            UserLoginRes res = UserWebAssembler.toLoginRes(user, token, userAppService.avatarUrl(user));
            recordLoginLog(request, httpRequest, user, true, "success", res.getToken());
            return Result.ok(res);
        }
        catch (RuntimeException e) {
            recordLoginLog(request, httpRequest, null, false, e.getMessage(), null);
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
                PasskeyWebAssembler.toAuthenticationStartCmd(request, PasskeyRelyingPartySupport.from(httpRequest)))));
    }

    @PostMapping("/passkeys/authentication")
    public Result<UserLoginRes> finishPasskeyAuthentication(@Valid @RequestBody PasskeyAuthenticationFinishRequest request, HttpServletRequest httpRequest) {
        try {
            User user = oauthPasskeyAppService.finishPasskeyAuthentication(
                    PasskeyWebAssembler.toAuthenticationFinishCmd(request, PasskeyRelyingPartySupport.from(httpRequest)));
            LoginTokenDTO token = loginTokenAppService.issueForLogin(user.getId());
            UserLoginRes res = UserWebAssembler.toLoginRes(user, token, userAppService.avatarUrl(user));
            recordLoginLog(request.getUsername(), httpRequest, user, true, "passkey success", res.getToken());
            return Result.ok(res);
        }
        catch (RuntimeException e) {
            recordLoginLog(request.getUsername(), httpRequest, null, false, e.getMessage(), null);
            throw e;
        }
    }

    private void recordLoginLog(UserLoginRequest request, HttpServletRequest httpRequest, User user, boolean success, String message, String token) {
        systemMonitorAppService.recordLoginLog(UserWebAssembler.toLoginLogDTO(
                request, user, success, message, clientIp(httpRequest), httpRequest.getHeader("User-Agent"), token));
    }

    private void recordLoginLog(String username, HttpServletRequest httpRequest, User user, boolean success, String message, String token) {
        systemMonitorAppService.recordLoginLog(UserWebAssembler.toLoginLogDTO(
                username, user, success, message, clientIp(httpRequest), httpRequest.getHeader("User-Agent"), token));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp : request.getRemoteAddr();
    }

    @GetMapping("/me/profile")
    public Result<UserProfileRes> profile() {
        return Result.ok(UserWebAssembler.toProfileRes(userAppService.profile(StpUtil.getLoginIdAsLong())));
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
        return Result.ok(UserWebAssembler.toDeptResList(userContextAppService.listDepts(StpUtil.getLoginIdAsLong())));
    }

    @GetMapping("/me/roles")
    public Result<List<UserRoleRes>> listMyRoles() {
        return Result.ok(UserWebAssembler.toRoleResList(userContextAppService.listRoles(StpUtil.getLoginIdAsLong())));
    }

    @GetMapping("/me/context")
    public Result<UserContextRes> getMyContext() {
        return Result.ok(UserWebAssembler.toContextRes(userContextAppService.getContext(StpUtil.getLoginIdAsLong())));
    }

    @PostMapping("/me/switch-dept")
    public Result<Void> switchDept(@Valid @RequestBody UserSwitchDeptRequest request) {
        userContextAppService.switchDept(StpUtil.getLoginIdAsLong(), request.getDeptId());
        return Result.ok();
    }

    @PostMapping("/me/switch-role")
    public Result<Void> switchRole(@Valid @RequestBody UserSwitchRoleRequest request) {
        userContextAppService.switchRole(StpUtil.getLoginIdAsLong(), request.getRoleId());
        return Result.ok();
    }

    @GetMapping("/permissions")
    public Result<PermissionListVO> permissions() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(UserWebAssembler.toPermissionListVO(
                permissionAppService.getUserPermissions(userId),
                userAppService.isEmailVerified(userId)));
    }
}
