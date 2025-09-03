package online.yudream.spring.admin.controller;

import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.SystemInfoService;
import online.yudream.spring.entity.system.SystemData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system")
public class SystemInfoController {
    @Resource
    private SystemInfoService systemInfoService;

    @GetMapping
    public SystemData getSystemInfo() {
        return systemInfoService.getSystemInfo();
    }
}
