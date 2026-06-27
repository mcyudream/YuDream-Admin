package online.yudream.base.interfaces.system.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.service.UserAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.user.assembler.UserWebAssembler;
import online.yudream.base.interfaces.system.user.request.UserLoginRequest;
import online.yudream.base.interfaces.system.user.res.UserRes;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserAppService userAppService;

    @PostMapping("/login")
    public Result<UserRes> login(@Valid @RequestBody UserLoginRequest request) {
       return  userAppService.login(UserWebAssembler.toLoginCmd(request))
               .map(UserWebAssembler::toUserRes)
               .map(Result::ok)
               .orElseGet(() -> Result.fail("用户不存在!"));
    }

}
