package online.yudream.base.interfaces.system.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.service.UserAppService;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.user.assembler.UserWebAssembler;
import online.yudream.base.interfaces.system.user.request.UserLoginRequest;
import online.yudream.base.interfaces.system.user.request.UserRegisterRequest;
import online.yudream.base.interfaces.system.user.res.UserLoginRes;
import online.yudream.base.interfaces.system.user.res.UserRegisterRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserAppService userAppService;

    @PostMapping("/register")
    public Result<UserRegisterRes> register(@Valid @RequestBody UserRegisterRequest request) {
        return Result.ok(UserWebAssembler.toRegisterRes(userAppService.register(UserWebAssembler.toRegisterCmd(request))));
    }

    @PostMapping("/login")
    public Result<UserLoginRes> login(@Valid @RequestBody UserLoginRequest request) {
        User user = userAppService.login(UserWebAssembler.toLoginCmd(request));
        StpUtil.login(user.getId());
        return Result.ok(UserLoginRes.builder()
                .token(StpUtil.getTokenValue())
                .tokenName(StpUtil.getTokenName())
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail() == null ? null : user.getEmail().getValue())
                .createTime(user.getCreateTime())
                .build());
    }

    @GetMapping("/verify-email")
    public Result<Void> verifyEmail(@RequestParam String token) {
        userAppService.verifyEmail(token);
        return Result.ok();
    }
}
