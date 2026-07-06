package online.yudream.base.interfaces.platform.dataviz.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.dataviz.query.ChartDefinitionPageQuery;
import online.yudream.base.application.platform.dataviz.service.ChartAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.dataviz.assembler.ChartWebAssembler;
import online.yudream.base.interfaces.platform.dataviz.request.ChartDataRequest;
import online.yudream.base.interfaces.platform.dataviz.request.ChartDefinitionSaveRequest;
import online.yudream.base.interfaces.platform.dataviz.request.ChartDefinitionUpdateRequest;
import online.yudream.base.interfaces.platform.dataviz.res.ChartDatasetRes;
import online.yudream.base.interfaces.platform.dataviz.res.ChartDefinitionRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/dataviz")
@RequiredArgsConstructor
public class ChartDataController {

    private final ChartAppService chartAppService;

    @GetMapping("/definitions")
    @PermissionRegister(code = "platform:dataviz:view", name = "查看图表定义", module = "平台能力", desc = "查看图表定义列表")
    public Result<PageResult<ChartDefinitionRes>> pageDefinitions(ChartDefinitionPageQuery query) {
        return Result.ok(ChartWebAssembler.toResPage(chartAppService.pageDefinitions(query)));
    }

    @PostMapping("/definitions")
    @PermissionRegister(code = "platform:dataviz:edit", name = "新增图表定义", module = "平台能力", desc = "新增图表定义")
    public Result<ChartDefinitionRes> createDefinition(@Valid @RequestBody ChartDefinitionSaveRequest request) {
        return Result.ok(ChartWebAssembler.toRes(chartAppService.createDefinition(ChartWebAssembler.toCmd(request))));
    }

    @PutMapping("/definitions/{id}")
    @PermissionRegister(code = "platform:dataviz:edit", name = "编辑图表定义", module = "平台能力", desc = "编辑图表定义")
    public Result<ChartDefinitionRes> updateDefinition(@PathVariable Long id,
                                                       @Valid @RequestBody ChartDefinitionUpdateRequest request) {
        return Result.ok(ChartWebAssembler.toRes(chartAppService.updateDefinition(ChartWebAssembler.toCmd(id, request))));
    }

    @DeleteMapping("/definitions/{id}")
    @PermissionRegister(code = "platform:dataviz:edit", name = "禁用图表定义", module = "平台能力", desc = "禁用图表定义")
    public Result<Void> disableDefinition(@PathVariable Long id) {
        chartAppService.disableDefinition(id);
        return Result.ok();
    }

    @PostMapping("/dataset")
    @PermissionRegister(code = "platform:dataviz:dataset", name = "查询图表数据集", module = "平台能力", desc = "查询图表数据集")
    public Result<ChartDatasetRes> queryDataset(@Valid @RequestBody ChartDataRequest request) {
        return Result.ok(ChartWebAssembler.toRes(chartAppService.queryDataset(ChartWebAssembler.toCmd(request))));
    }
}
