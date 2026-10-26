package online.yudream.base.interfaces.platform.mcpserver.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.mcpserver.service.McpServerAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * MCP 服务器接入引导页（能力 mcp-server）的管理端点：
 * 返回能力启停、完整/模块端点与逐模块工具清单（全量、不过调用者权限滤，
 * 每个工具附 permissionCode 供管理员对照授权）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/platform/mcpserver")
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.mcp-server", name = "enabled", havingValue = "true")
public class McpServerAdminController {

    private final McpServerAppService mcpServerAppService;

    @GetMapping("/overview")
    @PermissionRegister(code = "platform:mcpserver:view", name = "查看 MCP 服务器", module = "平台能力",
            desc = "查看 MCP 服务器端点、模块与工具清单（接入引导页）")
    public Result<Map<String, Object>> overview() {
        return Result.ok(mcpServerAppService.adminOverview());
    }
}
