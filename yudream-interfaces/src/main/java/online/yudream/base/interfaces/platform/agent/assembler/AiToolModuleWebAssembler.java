package online.yudream.base.interfaces.platform.agent.assembler;

import online.yudream.base.application.platform.ai.cmd.AiToolTestCmd;
import online.yudream.base.application.platform.ai.dto.AiToolCatalogRowDTO;
import online.yudream.base.application.platform.ai.dto.AiToolModuleDTO;
import online.yudream.base.application.platform.ai.dto.AiToolTestResultDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.platform.agent.request.AiToolTestRequest;
import online.yudream.base.interfaces.platform.agent.res.AiToolCatalogRowRes;
import online.yudream.base.interfaces.platform.agent.res.AiToolModuleRes;
import online.yudream.base.interfaces.platform.agent.res.AiToolTestRes;

import java.util.List;

public final class AiToolModuleWebAssembler {

    private AiToolModuleWebAssembler() {
    }

    public static AiToolTestCmd toCmd(AiToolTestRequest request, java.util.List<String> permissionCodes) {
        return AiToolTestCmd.builder()
                .code(request.getCode())
                .arguments(request.getArguments())
                .permissionCodes(permissionCodes)
                .build();
    }

    public static AiToolCatalogRowRes toRes(AiToolCatalogRowDTO dto) {
        if (dto == null) {
            return null;
        }
        return AiToolCatalogRowRes.builder()
                .toolId(dto.toolId())
                .code(dto.code())
                .name(dto.name())
                .description(dto.description())
                .source(dto.source())
                .moduleCode(dto.moduleCode())
                .moduleName(dto.moduleName())
                .moduleEnabled(dto.moduleEnabled())
                .moduleAlwaysOn(dto.moduleAlwaysOn())
                .risk(dto.risk())
                .requiresConfirmation(dto.requiresConfirmation())
                .enabled(dto.enabled())
                .editable(dto.editable())
                .deletable(dto.deletable())
                .testable(dto.testable())
                .permissionCode(dto.permissionCode())
                .inputSchema(dto.inputSchema())
                .build();
    }

    public static PageResult<AiToolCatalogRowRes> toRowPage(PageResult<AiToolCatalogRowDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(AiToolModuleWebAssembler::toRes).toList(),
                page.getTotal(), page.getPage(), page.getSize());
    }

    public static AiToolTestRes toRes(AiToolTestResultDTO dto) {
        if (dto == null) {
            return null;
        }
        return AiToolTestRes.builder()
                .success(dto.success())
                .message(dto.message())
                .action(dto.action())
                .toolCode(dto.toolCode())
                .source(dto.source())
                .durationMillis(dto.durationMillis())
                .payload(dto.payload())
                .build();
    }

    public static AiToolModuleRes toRes(AiToolModuleDTO dto) {
        if (dto == null) {
            return null;
        }
        List<AiToolModuleRes.Module> modules = dto.modules().stream()
                .map(module -> AiToolModuleRes.Module.builder()
                        .code(module.code())
                        .name(module.name())
                        .description(module.description())
                        .icon(module.icon())
                        .source(module.source())
                        .alwaysOn(module.alwaysOn())
                        .enabled(module.enabled())
                        .canDisable(module.canDisable())
                        .lazy(module.lazy())
                        .toolCount(module.toolCount())
                        .tools(module.tools().stream()
                                .map(tool -> AiToolModuleRes.Tool.builder()
                                        .code(tool.code())
                                        .name(tool.name())
                                        .description(tool.description())
                                        .permissionCode(tool.permissionCode())
                                        .risk(tool.risk())
                                        .source(tool.source())
                                        .requiresConfirmation(tool.requiresConfirmation())
                                        .enabled(tool.enabled())
                                        .build())
                                .toList())
                        .build())
                .toList();
        return AiToolModuleRes.builder()
                .systemEnabled(dto.systemEnabled())
                .configEnabled(dto.configEnabled())
                .onDemand(dto.onDemand())
                .modules(modules)
                .build();
    }
}
