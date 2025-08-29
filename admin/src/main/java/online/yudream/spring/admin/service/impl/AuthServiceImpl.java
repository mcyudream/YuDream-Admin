package online.yudream.spring.admin.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletRequest;
import online.yudream.spring.admin.service.AuthService;
import online.yudream.spring.base.exception.AuthException;
import online.yudream.spring.base.utils.BcryptHasher;
import online.yudream.spring.entity.dto.LoginDto;
import online.yudream.spring.entity.entity.User;
import online.yudream.spring.entity.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private BcryptHasher bcryptHasher;

    @Override
    public SaTokenInfo login(LoginDto loginDto, ServletRequest request) {
        User user = userMapper.findById(loginDto.username()).orElse(null);
        if (user == null) {
            throw new AuthException("exception.auth.notFound");
        } else if (this.bcryptHasher.matches(loginDto.password(), user.getPassword())) {
            StpUtil.login(user.getId());
            return StpUtil.getTokenInfo();
        } else {
            throw new AuthException("exception.auth.password.notMatch");
        }
    }
}
