package online.yudream.spring.admin.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.UserService;
import online.yudream.spring.base.common.SearchPageDto;
import online.yudream.spring.base.exception.AuthException;
import online.yudream.spring.base.utils.SearchUtils;
import online.yudream.spring.entity.entity.User;
import online.yudream.spring.entity.mapper.UserMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private SearchUtils searchUtils;

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

    @Override
    public Page<User> getUsersPage(SearchPageDto searchPageDto) {
        Criteria criteria = searchUtils.searchCriteria(searchPageDto);
        return searchUtils.findPage(User.class, searchPageDto, criteria);
    }

    @Override
    public void editUser(User user) {
        User rawUser = userMapper.findById(user.getId()).orElse(null);
        if (rawUser == null) {
            throw new AuthException("exception.user.notFound");
        }
        rawUser.setNickname(user.getNickname());
        rawUser.setEmail(user.getEmail());
        rawUser.setPhone(user.getPhone());
        rawUser.setStatus(user.getStatus());
        userMapper.save(rawUser);
    }

    @Override
    public void deleteUser(String id) {
        if (StpUtil.getLoginIdAsString().equals(id)) {
            throw new AuthException("exception.user.delete.self");
        }
        userMapper.deleteById(id);
    }


}
