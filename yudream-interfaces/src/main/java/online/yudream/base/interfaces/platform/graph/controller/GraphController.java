package online.yudream.base.interfaces.platform.graph.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.graph.query.GraphPageQuery;
import online.yudream.base.application.platform.graph.service.GraphAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.graph.assembler.GraphWebAssembler;
import online.yudream.base.interfaces.platform.graph.request.GraphConnectionSaveRequest;
import online.yudream.base.interfaces.platform.graph.request.GraphQueryRequest;
import online.yudream.base.interfaces.platform.graph.res.GraphConnectionRes;
import online.yudream.base.interfaces.platform.graph.res.GraphQueryLogRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphAppService graphAppService;

    @GetMapping("/connections")
    @PermissionRegister(code = "platform:graph:view", name = "查看图数据库", module = "平台能力", desc = "查看 Neo4j 图数据库连接")
    public Result<PageResult<GraphConnectionRes>> connections(GraphPageQuery query) {
        return Result.ok(GraphWebAssembler.toConnectionPage(graphAppService.pageConnections(query)));
    }

    @PostMapping("/connections")
    @PermissionRegister(code = "platform:graph:edit", name = "新增图数据库连接", module = "平台能力", desc = "新增 Neo4j 图数据库连接")
    public Result<GraphConnectionRes> createConnection(@Valid @RequestBody GraphConnectionSaveRequest request) {
        return Result.ok(GraphWebAssembler.toRes(graphAppService.saveConnection(GraphWebAssembler.toCmd(request))));
    }

    @PutMapping("/connections/{id}")
    @PermissionRegister(code = "platform:graph:edit", name = "编辑图数据库连接", module = "平台能力", desc = "编辑 Neo4j 图数据库连接")
    public Result<GraphConnectionRes> updateConnection(@PathVariable Long id, @Valid @RequestBody GraphConnectionSaveRequest request) {
        return Result.ok(GraphWebAssembler.toRes(graphAppService.saveConnection(GraphWebAssembler.toCmd(id, request))));
    }

    @DeleteMapping("/connections/{id}")
    @PermissionRegister(code = "platform:graph:edit", name = "停用图数据库连接", module = "平台能力", desc = "停用 Neo4j 图数据库连接")
    public Result<Void> disableConnection(@PathVariable Long id) {
        graphAppService.disableConnection(id);
        return Result.ok();
    }

    @PostMapping("/connections/{id}/enable")
    @PermissionRegister(code = "platform:graph:edit", name = "启用图数据库连接", module = "平台能力", desc = "启用 Neo4j 图数据库连接")
    public Result<Void> enableConnection(@PathVariable Long id) {
        graphAppService.enableConnection(id);
        return Result.ok();
    }

    @PostMapping("/connections/{id}/test")
    @PermissionRegister(code = "platform:graph:query", name = "测试图数据库连接", module = "平台能力", desc = "测试 Neo4j 图数据库连接")
    public Result<GraphQueryLogRes> testConnection(@PathVariable Long id) {
        return Result.ok(GraphWebAssembler.toRes(graphAppService.testConnection(id)));
    }

    @PostMapping("/connections/{id}/query")
    @PermissionRegister(code = "platform:graph:query", name = "执行Cypher查询", module = "平台能力", desc = "执行 Neo4j Cypher 查询")
    public Result<GraphQueryLogRes> query(@PathVariable Long id, @Valid @RequestBody GraphQueryRequest request) {
        return Result.ok(GraphWebAssembler.toRes(graphAppService.query(GraphWebAssembler.toCmd(id, request))));
    }

    @GetMapping("/query-logs")
    @PermissionRegister(code = "platform:graph:log:view", name = "查看图数据库日志", module = "平台能力", desc = "查看 Neo4j 查询日志")
    public Result<PageResult<GraphQueryLogRes>> logs(GraphPageQuery query) {
        return Result.ok(GraphWebAssembler.toLogPage(graphAppService.pageLogs(query)));
    }
}
