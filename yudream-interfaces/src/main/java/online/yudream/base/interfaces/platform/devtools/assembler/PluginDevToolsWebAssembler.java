package online.yudream.base.interfaces.platform.devtools.assembler;

import online.yudream.base.application.platform.devtools.cmd.PluginCommandTestCmd;
import online.yudream.base.application.platform.devtools.cmd.PluginDevProjectSaveCmd;
import online.yudream.base.application.platform.devtools.cmd.PluginScaffoldCmd;
import online.yudream.base.application.platform.devtools.dto.AgentTraceDetailDTO;
import online.yudream.base.application.platform.devtools.dto.AgentTracePageDTO;
import online.yudream.base.application.platform.devtools.dto.AgentTraceSummaryDTO;
import online.yudream.base.application.platform.devtools.dto.PluginDevPluginDTO;
import online.yudream.base.application.platform.devtools.dto.PluginDevToolsStatusDTO;
import online.yudream.base.application.platform.devtools.dto.PluginDisablePreviewDTO;
import online.yudream.base.application.platform.devtools.dto.PluginRuntimeAssetsDTO;
import online.yudream.base.application.platform.devtools.dto.PluginScaffoldDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceQuery;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandTestResult;
import online.yudream.base.domain.system.log.model.SystemLogEntry;
import online.yudream.base.interfaces.platform.devtools.request.PluginCommandTestRequest;
import online.yudream.base.interfaces.platform.devtools.request.PluginDevProjectSaveRequest;
import online.yudream.base.interfaces.platform.devtools.request.PluginScaffoldRequest;
import online.yudream.base.interfaces.platform.devtools.res.AgentTraceDetailRes;
import online.yudream.base.interfaces.platform.devtools.res.AgentTracePageRes;
import online.yudream.base.interfaces.platform.devtools.res.AgentTraceSummaryRes;
import online.yudream.base.interfaces.platform.devtools.res.PluginCommandTestRes;
import online.yudream.base.interfaces.platform.devtools.res.PluginDevPluginRes;
import online.yudream.base.interfaces.platform.devtools.res.PluginDevToolsStatusRes;
import online.yudream.base.interfaces.platform.devtools.res.PluginDisablePreviewRes;
import online.yudream.base.interfaces.platform.devtools.res.PluginLogEntryRes;
import online.yudream.base.interfaces.platform.devtools.res.PluginRuntimeAssetsRes;
import online.yudream.base.interfaces.platform.devtools.res.PluginScaffoldRes;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 开发者工具接口装配器：request → cmd、查询参数解析、DTO → res。
 */
public final class PluginDevToolsWebAssembler {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private PluginDevToolsWebAssembler() {
    }

    public static PluginDevProjectSaveCmd toCmd(PluginDevProjectSaveRequest request) {
        PluginDevProjectSaveCmd cmd = new PluginDevProjectSaveCmd();
        if (request == null) {
            return cmd;
        }
        cmd.setPath(request.getPath());
        cmd.setCode(request.getCode());
        cmd.setFrontendDist(request.getFrontendDist());
        cmd.setAutoCompile(request.getAutoCompile());
        cmd.setCompileCommand(request.getCompileCommand());
        return cmd;
    }

    public static PluginCommandTestCmd toCmd(PluginCommandTestRequest request) {
        PluginCommandTestCmd cmd = new PluginCommandTestCmd();
        if (request == null) {
            return cmd;
        }
        cmd.setCommand(request.getCommand());
        cmd.setArguments(request.getArguments());
        cmd.setContent(request.getContent());
        return cmd;
    }

    public static PluginScaffoldCmd toCmd(PluginScaffoldRequest request) {
        PluginScaffoldCmd cmd = new PluginScaffoldCmd();
        if (request == null) {
            return cmd;
        }
        cmd.setParentDir(request.getParentDir());
        cmd.setCode(request.getCode());
        cmd.setDisplayName(request.getDisplayName());
        cmd.setDescription(request.getDescription());
        cmd.setVersion(request.getVersion());
        cmd.setSpiVersion(request.getSpiVersion());
        cmd.setDepend(request.getDepend());
        cmd.setSoftdepend(request.getSoftdepend());
        cmd.setRegister(request.getRegister());
        return cmd;
    }

    public static AgentTraceQuery toQuery(String source, String pluginCode, String status, Integer page, Integer size) {
        return AgentTraceQuery.of(parseEnum(AgentTraceSource.class, source, "来源"),
                StringUtils.hasText(pluginCode) ? pluginCode.trim() : null,
                parseEnum(AgentTraceStatus.class, status, "状态"),
                page == null ? 1 : page,
                size == null ? 20 : size);
    }

    public static PluginDevToolsStatusRes toRes(PluginDevToolsStatusDTO dto) {
        return PluginDevToolsStatusRes.builder()
                .devModeEnabled(dto.isDevModeEnabled())
                .traceEnabled(dto.isTraceEnabled())
                .hostRunMode(dto.getHostRunMode())
                .devModeAuto(dto.isDevModeAuto())
                .devProjectStoreFile(dto.getDevProjectStoreFile())
                .devProjects(dto.getDevProjects())
                .installedCount(dto.getInstalledCount())
                .loadedCount(dto.getLoadedCount())
                .enabledCount(dto.getEnabledCount())
                .build();
    }

    public static List<PluginDevPluginRes> toPluginResList(List<PluginDevPluginDTO> plugins) {
        return plugins.stream().map(PluginDevToolsWebAssembler::toRes).toList();
    }

    public static PluginDevPluginRes toRes(PluginDevPluginDTO dto) {
        return PluginDevPluginRes.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .version(dto.getVersion())
                .description(dto.getDescription())
                .status(dto.getStatus())
                .loaded(dto.isLoaded())
                .enabled(dto.isEnabled())
                .devMode(dto.isDevMode())
                .devProject(dto.getDevProject())
                .dependencies(dto.getDependencies())
                .softDependencies(dto.getSoftDependencies())
                .build();
    }

    public static PluginDisablePreviewRes toRes(PluginDisablePreviewDTO dto) {
        return PluginDisablePreviewRes.builder()
                .code(dto.getCode())
                .blockers(dto.getBlockers())
                .softDependents(dto.getSoftDependents())
                .unloadBlockers(dto.getUnloadBlockers())
                .build();
    }

    public static PluginScaffoldRes toRes(PluginScaffoldDTO dto) {
        return PluginScaffoldRes.builder()
                .code(dto.getCode())
                .projectPath(dto.getProjectPath())
                .mainClass(dto.getMainClass())
                .spiVersion(dto.getSpiVersion())
                .files(dto.getFiles())
                .registered(dto.isRegistered())
                .build();
    }

    public static PluginRuntimeAssetsRes toRes(PluginRuntimeAssetsDTO dto) {
        return PluginRuntimeAssetsRes.builder()
                .pluginCode(dto.getPluginCode())
                .loaded(dto.isLoaded())
                .enabled(dto.isEnabled())
                .menus(dto.getMenus())
                .permissions(dto.getPermissions())
                .capabilities(dto.getCapabilities())
                .dashboardCards(dto.getDashboardCards())
                .frontendModules(dto.getFrontendModules())
                .httpEndpoints(dto.getHttpEndpoints())
                .commands(dto.getCommands())
                .messageInteractions(dto.getMessageInteractions())
                .aiTools(dto.getAiTools())
                .agents(dto.getAgents())
                .exposedServices(dto.getExposedServices())
                .build();
    }

    public static PluginCommandTestRes toRes(PluginCommandTestResult result) {
        return PluginCommandTestRes.builder()
                .pluginCode(result.pluginCode())
                .command(result.command())
                .matched(result.matched())
                .success(result.success())
                .errorMessage(result.errorMessage())
                .durationMs(result.durationMs())
                .build();
    }

    public static AgentTracePageRes toRes(AgentTracePageDTO dto) {
        return AgentTracePageRes.builder()
                .total(dto.getTotal())
                .page(dto.getPage())
                .size(dto.getSize())
                .list(dto.getList().stream().map(PluginDevToolsWebAssembler::toRes).toList())
                .build();
    }

    public static AgentTraceSummaryRes toRes(AgentTraceSummaryDTO dto) {
        return AgentTraceSummaryRes.builder()
                .traceId(dto.getTraceId())
                .source(dto.getSource())
                .ownerPluginCode(dto.getOwnerPluginCode())
                .agentId(dto.getAgentId())
                .agentCode(dto.getAgentCode())
                .agentName(dto.getAgentName())
                .status(dto.getStatus())
                .input(dto.getInput())
                .error(dto.getError())
                .stepCount(dto.getStepCount())
                .durationMs(dto.getDurationMs())
                .startTime(dto.getStartTime())
                .build();
    }

    public static AgentTraceDetailRes toRes(AgentTraceDetailDTO dto) {
        return AgentTraceDetailRes.builder()
                .traceId(dto.getTraceId())
                .source(dto.getSource())
                .ownerPluginCode(dto.getOwnerPluginCode())
                .agentId(dto.getAgentId())
                .agentCode(dto.getAgentCode())
                .agentName(dto.getAgentName())
                .status(dto.getStatus())
                .input(dto.getInput())
                .finalOutput(dto.getFinalOutput())
                .reasoning(dto.getReasoning())
                .error(dto.getError())
                .usage(dto.getUsage())
                .steps(dto.getSteps())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .durationMs(dto.getDurationMs())
                .build();
    }

    public static PluginLogEntryRes toLogRes(SystemLogEntry entry) {
        return new PluginLogEntryRes(
                entry.sequence(),
                entry.timestamp(),
                TIME.format(Instant.ofEpochMilli(entry.timestamp())),
                entry.level().name(),
                entry.logger(),
                entry.thread(),
                entry.traceId(),
                entry.message(),
                entry.throwable());
    }

    public static List<PluginLogEntryRes> toLogResList(List<SystemLogEntry> entries) {
        return entries.stream().map(PluginDevToolsWebAssembler::toLogRes).toList();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String label) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException("非法的" + label + "过滤值：" + value);
        }
    }
}
