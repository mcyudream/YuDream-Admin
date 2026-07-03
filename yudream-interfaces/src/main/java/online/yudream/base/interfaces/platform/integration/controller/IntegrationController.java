package online.yudream.base.interfaces.platform.integration.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.integration.query.IntegrationPageQuery;
import online.yudream.base.application.platform.integration.service.IntegrationAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.integration.assembler.IntegrationWebAssembler;
import online.yudream.base.interfaces.platform.integration.request.HttpConnectorSaveRequest;
import online.yudream.base.interfaces.platform.integration.request.HttpInvokeRequest;
import online.yudream.base.interfaces.platform.integration.request.RuntimeExecuteRequest;
import online.yudream.base.interfaces.platform.integration.request.RuntimeScriptSaveRequest;
import online.yudream.base.interfaces.platform.integration.res.HttpConnectorRes;
import online.yudream.base.interfaces.platform.integration.res.HttpInvocationLogRes;
import online.yudream.base.interfaces.platform.integration.res.RuntimeExecutionLogRes;
import online.yudream.base.interfaces.platform.integration.res.RuntimeScriptRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/integration")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationAppService integrationAppService;

    @GetMapping("/http-connectors")
    @PermissionRegister(code = "platform:integration:view", name = "查看集成调用", module = "平台能力", desc = "查看 HTTP 连接器")
    public Result<PageResult<HttpConnectorRes>> connectors(IntegrationPageQuery query) {
        return Result.ok(IntegrationWebAssembler.toConnectorPage(integrationAppService.pageConnectors(query)));
    }

    @PostMapping("/http-connectors")
    @PermissionRegister(code = "platform:integration:edit", name = "新增HTTP连接器", module = "平台能力", desc = "新增 HTTP 连接器")
    public Result<HttpConnectorRes> createConnector(@Valid @RequestBody HttpConnectorSaveRequest request) {
        return Result.ok(IntegrationWebAssembler.toRes(integrationAppService.saveConnector(IntegrationWebAssembler.toCmd(request))));
    }

    @PutMapping("/http-connectors/{id}")
    @PermissionRegister(code = "platform:integration:edit", name = "编辑HTTP连接器", module = "平台能力", desc = "编辑 HTTP 连接器")
    public Result<HttpConnectorRes> updateConnector(@PathVariable Long id, @Valid @RequestBody HttpConnectorSaveRequest request) {
        return Result.ok(IntegrationWebAssembler.toRes(integrationAppService.saveConnector(IntegrationWebAssembler.toCmd(id, request))));
    }

    @DeleteMapping("/http-connectors/{id}")
    @PermissionRegister(code = "platform:integration:edit", name = "禁用HTTP连接器", module = "平台能力", desc = "禁用 HTTP 连接器")
    public Result<Void> disableConnector(@PathVariable Long id) {
        integrationAppService.disableConnector(id);
        return Result.ok();
    }

    @PostMapping("/http-connectors/{id}/invoke")
    @PermissionRegister(code = "platform:integration:invoke", name = "执行HTTP调用", module = "平台能力", desc = "执行 HTTP 连接器调用")
    public Result<HttpInvocationLogRes> invoke(@PathVariable Long id, @RequestBody HttpInvokeRequest request) {
        return Result.ok(IntegrationWebAssembler.toRes(integrationAppService.invoke(IntegrationWebAssembler.toCmd(id, request))));
    }

    @GetMapping("/http-logs")
    @PermissionRegister(code = "platform:integration:log:view", name = "查看HTTP调用日志", module = "平台能力", desc = "查看 HTTP 调用日志")
    public Result<PageResult<HttpInvocationLogRes>> httpLogs(IntegrationPageQuery query) {
        return Result.ok(IntegrationWebAssembler.toHttpLogPage(integrationAppService.pageHttpLogs(query)));
    }

    @GetMapping("/runtime-scripts")
    @PermissionRegister(code = "platform:integration:view", name = "查看运行脚本", module = "平台能力", desc = "查看运行脚本")
    public Result<PageResult<RuntimeScriptRes>> scripts(IntegrationPageQuery query) {
        return Result.ok(IntegrationWebAssembler.toScriptPage(integrationAppService.pageScripts(query)));
    }

    @PostMapping("/runtime-scripts")
    @PermissionRegister(code = "platform:integration:edit", name = "新增运行脚本", module = "平台能力", desc = "新增运行脚本")
    public Result<RuntimeScriptRes> createScript(@Valid @RequestBody RuntimeScriptSaveRequest request) {
        return Result.ok(IntegrationWebAssembler.toRes(integrationAppService.saveScript(IntegrationWebAssembler.toCmd(request))));
    }

    @PutMapping("/runtime-scripts/{id}")
    @PermissionRegister(code = "platform:integration:edit", name = "编辑运行脚本", module = "平台能力", desc = "编辑运行脚本")
    public Result<RuntimeScriptRes> updateScript(@PathVariable Long id, @Valid @RequestBody RuntimeScriptSaveRequest request) {
        return Result.ok(IntegrationWebAssembler.toRes(integrationAppService.saveScript(IntegrationWebAssembler.toCmd(id, request))));
    }

    @DeleteMapping("/runtime-scripts/{id}")
    @PermissionRegister(code = "platform:integration:edit", name = "禁用运行脚本", module = "平台能力", desc = "禁用运行脚本")
    public Result<Void> disableScript(@PathVariable Long id) {
        integrationAppService.disableScript(id);
        return Result.ok();
    }

    @PostMapping("/runtime-scripts/{id}/execute")
    @PermissionRegister(code = "platform:integration:execute", name = "执行运行脚本", module = "平台能力", desc = "执行运行脚本")
    public Result<RuntimeExecutionLogRes> execute(@PathVariable Long id, @RequestBody RuntimeExecuteRequest request) {
        return Result.ok(IntegrationWebAssembler.toRes(integrationAppService.execute(IntegrationWebAssembler.toCmd(id, request))));
    }

    @GetMapping("/runtime-logs")
    @PermissionRegister(code = "platform:integration:log:view", name = "查看运行日志", module = "平台能力", desc = "查看运行脚本日志")
    public Result<PageResult<RuntimeExecutionLogRes>> runtimeLogs(IntegrationPageQuery query) {
        return Result.ok(IntegrationWebAssembler.toExecutionLogPage(integrationAppService.pageExecutionLogs(query)));
    }
}
