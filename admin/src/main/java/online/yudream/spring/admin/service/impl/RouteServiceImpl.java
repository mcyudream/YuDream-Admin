package online.yudream.spring.admin.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.RouteService;
import online.yudream.spring.entity.entity.route.Route;
import online.yudream.spring.entity.mapper.RouteMapper;
import online.yudream.spring.entity.vo.RouteVo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class RouteServiceImpl implements RouteService {
    @Resource
    private RouteMapper routeMapper;

    @Override
    public List<RouteVo> getRoutes() {
        List<Route> routes = routeMapper.findAll();
        return routes.stream().filter(route ->
            route.getParentId() == null && (route.getPermission() == null || StpUtil.hasPermission(route.getPermission())
        )).map(route -> routeToVo(route, routes)).toList();
    }

    private RouteVo routeToVo(Route route, List<Route> routes) {
        return RouteVo.builder()
                .meta(route.getMeta())
                .name(route.getName())
                .path(route.getPath())
                .component(route.getComponent())
                .redirect(route.getRedirect())
                .children(routes.stream()
                        .filter(route1 -> Objects.equals(route1.getParentId(), route.getId()) && (route1.getPermission() == null || StpUtil.hasPermission(route1.getPermission() )))
                        .map(route1 -> routeToVo(route1, routes)).toList()
                )
                .build();
    }

}
