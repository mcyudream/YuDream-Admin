package online.yudream.spring.admin.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletRequest;
import online.yudream.spring.admin.service.AuthService;
import online.yudream.spring.base.common.R;
import online.yudream.spring.entity.dto.LoginDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    @PostMapping("/login")
    public R<SaTokenInfo> login(@RequestBody LoginDto loginDto, ServletRequest request) {
        return R.success(authService.login(loginDto,request));
    }


}
