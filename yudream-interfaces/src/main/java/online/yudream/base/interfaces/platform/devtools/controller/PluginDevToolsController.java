package online.yudream.base.interfaces.platform.devtools.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.devtools.service.PluginDevToolsAppService;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevDirectoryBrowseInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevProjectInfo;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.devtools.assembler.PluginDevToolsWebAssembler;
import online.yudream.base.interfaces.platform.devtools.request.PluginCommandTestRequest;
import online.yudream.base.interfaces.platform.devtools.request.PluginDevProjectSaveRequest;
import online.yudream.base.interfaces.platform.devtools.res.AgentTraceDetailRes;
import online.yudream.base.interfaces.platform.devtools.res.AgentTracePageRes;
import online.yudream.base.interfaces.platform.devtools.res.PluginCommandTestRes;
import online.yudream.base.interfaces.platform.devtools.res.PluginDevPluginRes;
import online.yudream.base.interfaces.platform.devtools.res.PluginDevToolsStatusRes;
import online.yudream.base.interfaces.platform.devtools.res.PluginLogEntryRes;
import online.yudream.base.interfaces.platform.devtools.res.PluginRuntimeAssetsRes;
import online.yudream.base.interfaces.platform.devtools.service.PluginDevToolsSseBridge;
import online.yudream.base.interfaces.platform.devtools.service.PluginLogStreamBridge;
import online.yudream.base.interfaces.platform.plugin.assembler.PluginWebAssembler;
import online.yudream.base.interfaces.platform.plugin.res.PluginModuleRes;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 插件开发者工具接口：为全局悬浮调试抽屉提供状态、资产、追踪、事件流与指令模拟。
 */
@RestController
@RequestMapping("/api/platform/plugin-devtools")
@RequiredArgsConstructor
public class PluginDevToolsController {

    private final PluginDevToolsAppService devToolsAppService;
    private final PluginDevToolsSseBridge sseBridge;
    private final PluginLogStreamBridge logStreamBridge;

    @GetMapping("/status")
    @PermissionRegister(code = "platform:plugin-devtools:view", name = "查看开发者工具", module = "开发者工具", desc = "查看开发模式状态、插件资产与 Agent 追踪")
    public Result<PluginDevToolsStatusRes> status() {
        return Result.ok(PluginDevToolsWebAssembler.toRes(devToolsAppService.status()));
    }

    @GetMapping("/plugins")
    @PermissionRegister(code = "platform:plugin-devtools:view", name = "查看开发者工具插件清单", module = "开发者工具", desc = "查看插件清单与开发模式标记")
    public Result<List<PluginDevPluginRes>> plugins() {
        return Result.ok(PluginDevToolsWebAssembler.toPluginResList(devToolsAppService.plugins()));
    }

    @GetMapping("/plugins/{code}/assets")
    @PermissionRegister(code = "platform:plugin-devtools:view", name = "查看插件运行时资产", module = "开发者工具", desc = "查看插件注册的端点、权限、菜单、指令、工具等资产")
    public Result<PluginRuntimeAssetsRes> assets(@PathVariable String code) {
        return Result.ok(PluginDevToolsWebAssembler.toRes(devToolsAppService.assets(code)));
    }

    @PostMapping("/plugins/{code}/reload")
    @PermissionRegister(code = "platform:plugin-devtools:manage", name = "重载开发插件", module = "开发者工具", desc = "手动触发开发模式插件的回收、目录加载与启用")
    public Result<PluginModuleRes> reload(@PathVariable String code) {
        return Result.ok(PluginWebAssembler.toRes(devToolsAppService.reload(code)));
    }

    @PostMapping("/plugins/{code}/command-test")
    @PermissionRegister(code = "platform:plugin-devtools:manage", name = "模拟插件指令", module = "开发者工具", desc = "在指定插件作用域内模拟触发 QQ 指令处理器")
    public Result<PluginCommandTestRes> commandTest(@PathVariable String code,
                                                    @Valid @RequestBody PluginCommandTestRequest request) {
        return Result.ok(PluginDevToolsWebAssembler.toRes(
                devToolsAppService.commandTest(code, PluginDevToolsWebAssembler.toCmd(request))));
    }

    @GetMapping("/dev-projects")
    @PermissionRegister(code = "platform:plugin-devtools:view", name = "查看开发模式项目", module = "开发者工具", desc = "查看配置与面板登记的开发模式插件项目及目录状态")
    public Result<List<PluginDevProjectInfo>> devProjects() {
        return Result.ok(devToolsAppService.devProjects());
    }

    @GetMapping("/dev-projects/browse")
    @PermissionRegister(code = "platform:plugin-devtools:manage", name = "浏览宿主机目录", module = "开发者工具", desc = "登记开发项目时浏览宿主机目录，仅列子目录与插件模块标记，不读取文件内容")
    public Result<PluginDevDirectoryBrowseInfo> browseDevDirectories(@RequestParam(required = false) String path) {
        return Result.ok(devToolsAppService.browseDevDirectories(path));
    }

    @PostMapping("/dev-projects")
    @PermissionRegister(code = "platform:plugin-devtools:manage", name = "登记开发模式项目", module = "开发者工具", desc = "将插件源码目录登记到面板清单并持久化，已启用插件立即切换源码加载")
    public Result<PluginDevProjectInfo> addDevProject(@Valid @RequestBody PluginDevProjectSaveRequest request) {
        return Result.ok(devToolsAppService.addDevProject(PluginDevToolsWebAssembler.toCmd(request)));
    }

    @DeleteMapping("/dev-projects/{code}")
    @PermissionRegister(code = "platform:plugin-devtools:manage", name = "移除开发模式项目", module = "开发者工具", desc = "从面板清单移除开发模式项目登记")
    public Result<Void> removeDevProject(@PathVariable String code) {
        devToolsAppService.removeDevProject(code);
        return Result.ok();
    }

    @GetMapping("/agent-traces")
    @PermissionRegister(code = "platform:plugin-devtools:view", name = "查看 Agent 执行追踪", module = "开发者工具", desc = "按来源、插件与状态分页查询 Agent 执行追踪")
    public Result<AgentTracePageRes> traces(@RequestParam(required = false) String source,
                                            @RequestParam(required = false) String pluginCode,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        return Result.ok(PluginDevToolsWebAssembler.toRes(
                devToolsAppService.traces(PluginDevToolsWebAssembler.toQuery(source, pluginCode, status, page, size))));
    }

    @GetMapping("/agent-traces/{traceId}")
    @PermissionRegister(code = "platform:plugin-devtools:view", name = "查看 Agent 追踪详情", module = "开发者工具", desc = "查看单次执行的完整步骤、思考过程与用量")
    public Result<AgentTraceDetailRes> traceDetail(@PathVariable String traceId) {
        return Result.ok(PluginDevToolsWebAssembler.toRes(devToolsAppService.traceDetail(traceId)));
    }

    @GetMapping(value = "/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:plugin-devtools:view", name = "订阅插件事件流", module = "开发者工具", desc = "实时接收插件生命周期与热重载事件")
    public SseEmitter lifecycleStream() {
        return sseBridge.connectLifecycle();
    }

    @GetMapping(value = "/agent-traces/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:plugin-devtools:view", name = "订阅 Agent 追踪流", module = "开发者工具", desc = "实时接收 Agent 执行步骤增量与完成事件")
    public SseEmitter traceStream() {
        return sseBridge.connectTraces();
    }

    @GetMapping("/plugins/{code}/logs")
    @PermissionRegister(code = "platform:plugin-devtools:view", name = "查看插件运行日志", module = "开发者工具", desc = "按插件 logger 前缀查询最近的运行日志")
    public Result<List<PluginLogEntryRes>> logs(@PathVariable String code,
                                                @RequestParam(required = false) String level,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer limit) {
        return Result.ok(PluginDevToolsWebAssembler.toLogResList(
                devToolsAppService.recentPluginLogs(code, level, keyword, limit)));
    }

    @GetMapping(value = "/plugins/{code}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:plugin-devtools:view", name = "订阅插件日志流", module = "开发者工具", desc = "实时接收指定插件的运行日志")
    public SseEmitter logStream(@PathVariable String code,
                                @RequestParam(required = false) String level) {
        return logStreamBridge.connect(code, level);
    }
}
