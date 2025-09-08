package online.yudream.spring.admin.controller;

import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.RouteService;
import online.yudream.spring.base.common.R;
import online.yudream.spring.entity.vo.RouteVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/route")
public class RouteController {
    @Resource
    private RouteService routeService;

    @GetMapping
    public R<List<RouteVo>> getRoutes() {
        return R.success(routeService.getRoutes());

    }
}
