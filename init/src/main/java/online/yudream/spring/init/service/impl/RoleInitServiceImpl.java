package online.yudream.spring.init.service.impl;

import jakarta.annotation.Resource;
import online.yudream.spring.entity.entity.Role;
import online.yudream.spring.entity.mapper.RoleMapper;
import online.yudream.spring.init.initenums.SysRole;
import online.yudream.spring.init.service.InitService;
import org.springframework.stereotype.Service;

@Service
public class RoleInitServiceImpl implements InitService {
    @Resource
    private RoleMapper roleMapper;

    @Override
    public void init() {
        for (SysRole sysRole : SysRole.values()) {
            Role role = sysRole.getRole();
            roleMapper.save(role);
        }
    }

    @Override
    public boolean isFirstInit() {
        return roleMapper.count() == 0;
    }
}
