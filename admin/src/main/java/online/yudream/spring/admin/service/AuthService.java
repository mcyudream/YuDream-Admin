package online.yudream.spring.admin.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import jakarta.servlet.ServletRequest;
import online.yudream.spring.entity.dto.LoginDto;

public interface AuthService {
    SaTokenInfo login(LoginDto loginDto, ServletRequest request);
}
