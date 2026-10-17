package online.yudream.base.interfaces.platform.agent.assembler;

import online.yudream.base.application.platform.ai.dto.AiToolModuleDTO;
import online.yudream.base.interfaces.platform.agent.res.AiToolModuleRes;

import java.util.List;

public final class AiToolModuleWebAssembler {

    private AiToolModuleWebAssembler() {
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
