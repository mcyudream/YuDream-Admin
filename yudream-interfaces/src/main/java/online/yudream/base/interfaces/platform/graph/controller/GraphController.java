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
import online.yudream.base.interfaces.platform.graph.res.GraphConnectionRes;
import online.yudream.base.interfaces.platform.graph.res.GraphQueryLogRes;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/platform/graph") @RequiredArgsConstructor
public class GraphController {
    private final GraphAppService graphAppService;
    @GetMapping("/tables") @PermissionRegister(code="platform:graph:view",name="查看逻辑图表",module="平台能力",desc="查看 Neo4j 逻辑图表")
    public Result<PageResult<GraphConnectionRes>> tables(GraphPageQuery query){return Result.ok(GraphWebAssembler.toConnectionPage(graphAppService.pageTables(query)));}
    @PostMapping("/tables") @PermissionRegister(code="platform:graph:edit",name="新增逻辑图表",module="平台能力",desc="新增 Neo4j 逻辑图表")
    public Result<GraphConnectionRes> create(@Valid @RequestBody GraphConnectionSaveRequest request){return Result.ok(GraphWebAssembler.toRes(graphAppService.saveTable(GraphWebAssembler.toCmd(request))));}
    @PutMapping("/tables/{id}") @PermissionRegister(code="platform:graph:edit",name="编辑逻辑图表",module="平台能力",desc="编辑 Neo4j 逻辑图表")
    public Result<GraphConnectionRes> update(@PathVariable Long id,@Valid @RequestBody GraphConnectionSaveRequest request){return Result.ok(GraphWebAssembler.toRes(graphAppService.saveTable(GraphWebAssembler.toCmd(id,request))));}
    @DeleteMapping("/tables/{id}") @PermissionRegister(code="platform:graph:edit",name="停用逻辑图表",module="平台能力",desc="停用 Neo4j 逻辑图表")
    public Result<Void> disable(@PathVariable Long id){graphAppService.disableTable(id);return Result.ok();}
    @PostMapping("/tables/{id}/enable") @PermissionRegister(code="platform:graph:edit",name="启用逻辑图表",module="平台能力",desc="启用 Neo4j 逻辑图表")
    public Result<Void> enable(@PathVariable Long id){graphAppService.enableTable(id);return Result.ok();}
    @PostMapping("/tables/{id}/test") @PermissionRegister(code="platform:graph:query",name="诊断逻辑图表",module="平台能力",desc="诊断 Neo4j 部署连接")
    public Result<GraphQueryLogRes> test(@PathVariable Long id){return Result.ok(GraphWebAssembler.toRes(graphAppService.testTable(id)));}
    @GetMapping("/query-logs") @PermissionRegister(code="platform:graph:log:view",name="查看逻辑图表日志",module="平台能力",desc="查看 Neo4j 查询日志")
    public Result<PageResult<GraphQueryLogRes>> logs(GraphPageQuery query){return Result.ok(GraphWebAssembler.toLogPage(graphAppService.pageLogs(query)));}
}
