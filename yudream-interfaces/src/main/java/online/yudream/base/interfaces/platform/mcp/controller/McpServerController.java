package online.yudream.base.interfaces.platform.mcp.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.mcp.service.McpServerAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.mcp.assembler.McpWebAssembler;
import online.yudream.base.interfaces.platform.mcp.request.McpServerSaveRequest;
import online.yudream.base.interfaces.platform.mcp.res.McpServerRes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MCP 服务端管理：注册、测试连接、刷新工具清单。
 * 运行期由 AI 工具模块系统按 mcp:&lt;serverCode&gt; 模块提供工具。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/platform/mcp/servers")
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.mcp", name = "enabled", havingValue = "true")
public class McpServerController {

    private final McpServerAppService mcpServerAppService;

    @GetMapping
    @PermissionRegister(code = "platform:mcp:server:view", name = "查看 MCP 服务", module = "平台能力", desc = "查看 MCP 服务端列表与工具清单")
    public Result<List<McpServerRes>> list() {
        return Result.ok(mcpServerAppService.list().stream().map(McpWebAssembler::toRes).toList());
    }

    @PostMapping
    @PermissionRegister(code = "platform:mcp:server:create", name = "新建 MCP 服务", module = "平台能力", desc = "注册 MCP 服务端并自动测试连接")
    public Result<McpServerRes> create(@Valid @RequestBody McpServerSaveRequest request) {
        return Result.ok(McpWebAssembler.toRes(mcpServerAppService.create(McpWebAssembler.toCmd(request))));
    }

    @PutMapping("/{id}")
    @PermissionRegister(code = "platform:mcp:server:edit", name = "编辑 MCP 服务", module = "平台能力", desc = "编辑 MCP 服务端连接参数")
    public Result<McpServerRes> update(@PathVariable Long id, @Valid @RequestBody McpServerSaveRequest request) {
        return Result.ok(McpWebAssembler.toRes(mcpServerAppService.update(McpWebAssembler.toCmd(id, request))));
    }

    @DeleteMapping("/{id}")
    @PermissionRegister(code = "platform:mcp:server:delete", name = "删除 MCP 服务", module = "平台能力", desc = "删除 MCP 服务端登记")
    public Result<Void> delete(@PathVariable Long id) {
        mcpServerAppService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/test")
    @PermissionRegister(code = "platform:mcp:server:test", name = "测试 MCP 服务", module = "平台能力", desc = "连接 MCP 服务端并刷新工具清单")
    public Result<McpServerRes> test(@PathVariable Long id) {
        return Result.ok(McpWebAssembler.toRes(mcpServerAppService.test(id)));
    }
}
