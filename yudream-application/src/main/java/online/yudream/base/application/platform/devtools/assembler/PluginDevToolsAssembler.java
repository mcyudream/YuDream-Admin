package online.yudream.base.application.platform.devtools.assembler;

import online.yudream.base.application.platform.devtools.dto.AgentTraceDetailDTO;
import online.yudream.base.application.platform.devtools.dto.AgentTraceSummaryDTO;
import online.yudream.base.application.platform.devtools.dto.PluginDevPluginDTO;
import online.yudream.base.application.platform.devtools.dto.PluginRuntimeAssetsDTO;
import online.yudream.base.application.platform.devtools.dto.PluginScaffoldDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.domain.platform.agent.aggregate.AgentExecutionTrace;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevProjectInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginRuntimeAssets;
import online.yudream.base.domain.platform.plugin.valobj.PluginScaffoldResult;

import java.util.List;

/**
 * 开发者工具应用装配器：领域快照/聚合 → 应用 DTO。
 */
public final class PluginDevToolsAssembler {

    private PluginDevToolsAssembler() {
    }

    public static PluginDevPluginDTO toPluginDTO(PluginModuleDTO module, PluginDevProjectInfo devProject) {
        return PluginDevPluginDTO.builder()
                .code(module.getCode())
                .name(module.getName())
                .version(module.getVersion())
                .description(module.getDescription())
                .status(module.getStatus())
                .loaded(module.isLoaded())
                .enabled(module.isEnabled())
                .devMode(devProject != null)
                .devProject(devProject)
                .dependencies(module.getDependencies() == null ? List.of() : List.copyOf(module.getDependencies()))
                .softDependencies(module.getSoftDependencies() == null ? List.of() : List.copyOf(module.getSoftDependencies()))
                .build();
    }

    public static PluginScaffoldDTO toScaffoldDTO(PluginScaffoldResult result, boolean registered) {
        return PluginScaffoldDTO.builder()
                .code(result.code())
                .projectPath(result.projectPath())
                .mainClass(result.mainClass())
                .spiVersion(result.spiVersion())
                .files(result.files())
                .registered(registered)
                .build();
    }

    public static PluginRuntimeAssetsDTO toAssetsDTO(PluginRuntimeAssets assets) {
        return PluginRuntimeAssetsDTO.builder()
                .pluginCode(assets.pluginCode())
                .loaded(assets.loaded())
                .enabled(assets.enabled())
                .menus(assets.menus())
                .permissions(assets.permissions())
                .capabilities(assets.capabilities())
                .dashboardCards(assets.dashboardCards())
                .frontendModules(assets.frontendModules())
                .httpEndpoints(assets.httpEndpoints())
                .commands(assets.commands())
                .messageInteractions(assets.messageInteractions())
                .aiTools(assets.aiTools())
                .agents(assets.agents())
                .exposedServices(assets.exposedServices())
                .build();
    }

    public static AgentTraceSummaryDTO toSummaryDTO(AgentExecutionTrace trace) {
        return AgentTraceSummaryDTO.builder()
                .traceId(trace.getTraceId())
                .source(trace.getSource())
                .ownerPluginCode(trace.getOwnerPluginCode())
                .agentId(trace.getAgentId() == null ? null : String.valueOf(trace.getAgentId()))
                .agentCode(trace.getAgentCode())
                .agentName(trace.getAgentName())
                .status(trace.getStatus())
                .input(trace.getInput())
                .error(trace.getError())
                .stepCount(trace.getSteps() == null ? 0 : trace.getSteps().size())
                .durationMs(trace.getDurationMs())
                .startTime(trace.getStartTime())
                .build();
    }

    public static AgentTraceDetailDTO toDetailDTO(AgentExecutionTrace trace) {
        return AgentTraceDetailDTO.builder()
                .traceId(trace.getTraceId())
                .source(trace.getSource())
                .ownerPluginCode(trace.getOwnerPluginCode())
                .agentId(trace.getAgentId() == null ? null : String.valueOf(trace.getAgentId()))
                .agentCode(trace.getAgentCode())
                .agentName(trace.getAgentName())
                .status(trace.getStatus())
                .input(trace.getInput())
                .finalOutput(trace.getFinalOutput())
                .reasoning(trace.getReasoning())
                .error(trace.getError())
                .usage(trace.getUsage())
                .steps(trace.getSteps() == null ? List.of() : trace.getSteps())
                .startTime(trace.getStartTime())
                .endTime(trace.getEndTime())
                .durationMs(trace.getDurationMs())
                .build();
    }
}
