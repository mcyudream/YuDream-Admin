package online.yudream.spring.init.service.impl;


import jakarta.annotation.Resource;
import online.yudream.spring.base.utils.BcryptHasher;
import online.yudream.spring.entity.entity.User;
import online.yudream.spring.entity.entity.common.DepartmentRoleEntity;
import online.yudream.spring.entity.enums.UserStatus;
import online.yudream.spring.entity.mapper.UserMapper;
import online.yudream.spring.init.initenums.SysDepartment;
import online.yudream.spring.init.initenums.SysRole;
import online.yudream.spring.init.service.InitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserInitServiceImpl implements InitService {


    @Resource
    private UserMapper userMapper;

    @Value("${init.user.username}")
    private String username;

    @Value("${init.user.password}")
    private String password;

    @Resource
    private BcryptHasher bcryptHasher;

    @Override
    public void init() {
        User user = User.builder()
                .id(username)
                .nickname(username)
                .password(bcryptHasher.hash(password))
                .status(UserStatus.NORMAL)
                .departmentRoles(
                        List.of(
                                DepartmentRoleEntity.builder()
                                        .department(SysDepartment.ADMIN.getDepartment())
                                        .build()
                        )
                )
                .roles(List.of(
                        SysRole.SUPER.getRole()))
                .build();
        userMapper.save(user);
    }

    @Override
    public boolean isFirstInit() {
        return userMapper.count() == 0;
    }
}
