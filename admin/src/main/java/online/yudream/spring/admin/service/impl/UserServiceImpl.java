package online.yudream.spring.admin.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.UserService;
import online.yudream.spring.base.common.SearchPageDto;
import online.yudream.spring.base.exception.AuthException;
import online.yudream.spring.base.utils.SearchUtils;
import online.yudream.spring.entity.entity.Department;
import online.yudream.spring.entity.entity.User;
import online.yudream.spring.entity.entity.common.DepartmentRoleEntity;
import online.yudream.spring.entity.mapper.DepartmentMapper;
import online.yudream.spring.entity.mapper.UserMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private SearchUtils searchUtils;

    @Resource
    private DepartmentMapper departmentMapper;

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
        if (!Objects.equals(searchPageDto.extraId(), "all")) {
            criteria.andOperator(Criteria.where("departmentRoles.department.id").is(searchPageDto.extraId()));
        }
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

    @Override
    public User addToDepartment(String userId, String departmentId) {
        User user = userMapper.findById(userId).orElse(null);
        if (user == null) {
            throw new AuthException("exception.user.notFound");
        }
        Department department = departmentMapper.findById(departmentId).orElse(null);
        if (department == null) {
            throw new AuthException("exception.department.notFound");
        }
        user.getDepartmentRoles().add(DepartmentRoleEntity.builder()
                        .department(department)
                .build());
        return userMapper.save(user);
    }

    @Override
    public User deleteDepartment(String userId, String departmentId) {
        User user = userMapper.findById(userId).orElse(null);
        if (user == null) {
            throw new AuthException("exception.user.notFound");
        }
        Department department = departmentMapper.findById(departmentId).orElse(null);
        if (department == null) {
            throw new AuthException("exception.department.notFound");
        }
        user.setDepartmentRoles(user.getDepartmentRoles().stream().filter(item->!item.getDepartment().getId().equals(departmentId)).collect(Collectors.toList()));
        return userMapper.save(user);
    }
}
