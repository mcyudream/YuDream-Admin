package online.yudream.spring.admin.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.UserService;
import online.yudream.spring.base.exception.AuthException;
import online.yudream.spring.entity.entity.User;
import online.yudream.spring.entity.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public User userInfo() {
        String username = StpUtil.getLoginId().toString();
        User user = userMapper.findById(username).orElse(null);
        if (user == null) {
            throw new AuthException("exception.auth.notFound");
        } else {
            return user;
        }
    }
}
