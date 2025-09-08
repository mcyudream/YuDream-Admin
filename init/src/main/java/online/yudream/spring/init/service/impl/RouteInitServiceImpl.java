package online.yudream.spring.init.service.impl;

import jakarta.annotation.Resource;
import online.yudream.spring.entity.mapper.RouteMapper;
import online.yudream.spring.init.initenums.SysRoute;
import online.yudream.spring.init.service.InitService;
import org.springframework.stereotype.Service;

@Service
public class RouteInitServiceImpl implements InitService {
    @Resource
    private  RouteMapper routeMapper;
    @Override
    public void init() {
        for (SysRoute sysRoute : SysRoute.values()) {
            routeMapper.save(sysRoute.getRoute());
        }
    }

    @Override
    public boolean isFirstInit() {
        return routeMapper.count() == 0;
    }
}
