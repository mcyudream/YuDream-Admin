package online.yudream.base.interfaces.platform.agent.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.service.AgentTraceAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.agent.assembler.AgentWebAssembler;
import online.yudream.base.interfaces.platform.agent.res.AgentTraceDetailRes;
import online.yudream.base.interfaces.platform.agent.res.AgentTracePageRes;
import online.yudream.base.interfaces.platform.agent.res.AgentTraceStatsRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 执行流查询与分析：日志菜单独立页面，不依赖开发模式。
 */
@RestController
@RequestMapping("/api/platform/agent-traces")
@RequiredArgsConstructor
public class AgentTraceController {

    private final AgentTraceAppService agentTraceAppService;

    @GetMapping
    @PermissionRegister(code = "system:monitor:agent-trace:view", name = "查看 Agent 执行流", module = "系统管理", desc = "分页查询 Agent 执行追踪")
    public Result<AgentTracePageRes> page(@RequestParam(required = false) String source,
                                          @RequestParam(required = false) String pluginCode,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String agentCode,
                                          @RequestParam(required = false) String startTime,
                                          @RequestParam(required = false) String endTime,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return Result.ok(AgentWebAssembler.toTracePage(agentTraceAppService.page(
                AgentWebAssembler.toTraceQuery(source, pluginCode, status, keyword, agentCode, startTime, endTime, page, size))));
    }

    @GetMapping("/stats")
    @PermissionRegister(code = "system:monitor:agent-trace:view", name = "查看 Agent 执行流统计", module = "系统管理", desc = "按当前筛选条件聚合 Agent 执行追踪")
    public Result<AgentTraceStatsRes> stats(@RequestParam(required = false) String source,
                                            @RequestParam(required = false) String pluginCode,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String agentCode,
                                            @RequestParam(required = false) String startTime,
                                            @RequestParam(required = false) String endTime) {
        return Result.ok(AgentWebAssembler.toTraceStats(agentTraceAppService.stats(
                AgentWebAssembler.toTraceQuery(source, pluginCode, status, keyword, agentCode, startTime, endTime, 1, 20))));
    }

    @GetMapping("/{traceId}")
    @PermissionRegister(code = "system:monitor:agent-trace:view", name = "查看 Agent 执行流详情", module = "系统管理", desc = "查看单次 Agent 执行的步骤、思考过程与用量")
    public Result<AgentTraceDetailRes> detail(@PathVariable String traceId) {
        return Result.ok(AgentWebAssembler.toTraceDetail(agentTraceAppService.detail(traceId)));
    }

    @DeleteMapping("/{traceId}")
    @PermissionRegister(code = "system:monitor:agent-trace:delete", name = "删除 Agent 执行流", module = "系统管理", desc = "删除单条 Agent 执行追踪")
    public Result<Long> delete(@PathVariable String traceId) {
        return Result.ok(agentTraceAppService.delete(traceId));
    }

    @DeleteMapping
    @PermissionRegister(code = "system:monitor:agent-trace:delete", name = "清空 Agent 执行流", module = "系统管理", desc = "按当前筛选条件清空 Agent 执行追踪")
    public Result<Long> clear(@RequestParam(required = false) String source,
                              @RequestParam(required = false) String pluginCode,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(required = false) String agentCode,
                              @RequestParam(required = false) String startTime,
                              @RequestParam(required = false) String endTime) {
        return Result.ok(agentTraceAppService.clear(
                AgentWebAssembler.toTraceQuery(source, pluginCode, status, keyword, agentCode, startTime, endTime, 1, 20)));
    }
}
