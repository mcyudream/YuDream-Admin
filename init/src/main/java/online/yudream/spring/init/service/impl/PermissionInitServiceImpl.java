package online.yudream.spring.init.service.impl;

import jakarta.annotation.Resource;
import online.yudream.spring.entity.mapper.PermissionMapper;
import online.yudream.spring.init.initenums.SysPermission;
import online.yudream.spring.init.service.InitService;
import org.springframework.stereotype.Service;

@Service
public class PermissionInitServiceImpl implements InitService {
    @Resource
    private PermissionMapper permissionMapper;
    @Override
    public void init() {
        for (SysPermission sysPermission : SysPermission.values()) {
            permissionMapper.save(sysPermission.getPermission());
        }
    }

    @Override
    public boolean isFirstInit() {
        return permissionMapper.count() == 0;
    }
}
