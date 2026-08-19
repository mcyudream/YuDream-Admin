package online.yudream.base.application.platform.devtools.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.service.AgentTraceProperties;
import online.yudream.base.application.platform.devtools.assembler.PluginDevToolsAssembler;
import online.yudream.base.application.platform.devtools.cmd.PluginCommandTestCmd;
import online.yudream.base.application.platform.devtools.dto.AgentTraceDetailDTO;
import online.yudream.base.application.platform.devtools.dto.AgentTracePageDTO;
import online.yudream.base.application.platform.devtools.dto.PluginDevPluginDTO;
import online.yudream.base.application.platform.devtools.dto.PluginDevToolsStatusDTO;
import online.yudream.base.application.platform.devtools.dto.PluginRuntimeAssetsDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.repo.AgentExecutionTraceRepo;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceQuery;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandTestResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevProjectInfo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 插件开发者工具应用服务：为悬浮调试抽屉提供状态、资产、追踪与指令模拟数据面。
 */
@Service
@RequiredArgsConstructor
public class PluginDevToolsAppService {

    private final PluginRuntimeGateway runtimeGateway;
    private final PluginAppService pluginAppService;
    private final AgentExecutionTraceRepo traceRepo;
    private final AgentTraceProperties traceProperties;

    public PluginDevToolsStatusDTO status() {
        List<PluginModuleDTO> installed = pluginAppService.listInstalled();
        return PluginDevToolsStatusDTO.builder()
                .devModeEnabled(runtimeGateway.devModeEnabled())
                .traceEnabled(traceProperties.isEnabled())
                .devProjects(runtimeGateway.devModeProjects())
                .installedCount(installed.size())
                .loadedCount((int) installed.stream().filter(PluginModuleDTO::isLoaded).count())
                .enabledCount((int) installed.stream().filter(PluginModuleDTO::isEnabled).count())
                .build();
    }

    public List<PluginDevPluginDTO> plugins() {
        Map<String, PluginDevProjectInfo> devProjects = runtimeGateway.devModeProjects().stream()
                .collect(Collectors.toMap(PluginDevProjectInfo::code, Function.identity(), (left, right) -> left));
        return pluginAppService.listInstalled().stream()
                .map(module -> PluginDevToolsAssembler.toPluginDTO(module, devProjects.get(module.getCode())))
                .toList();
    }

    public PluginRuntimeAssetsDTO assets(String code) {
        requireCode(code);
        return PluginDevToolsAssembler.toAssetsDTO(runtimeGateway.runtimeAssets(code.trim()));
    }

    public PluginModuleDTO reload(String code) {
        requireCode(code);
        return pluginAppService.reloadDevPlugin(code.trim());
    }

    public AgentTracePageDTO traces(AgentTraceQuery query) {
        return AgentTracePageDTO.builder()
                .total(traceRepo.count(query))
                .page(query.page())
                .size(query.size())
                .list(traceRepo.query(query).stream().map(PluginDevToolsAssembler::toSummaryDTO).toList())
                .build();
    }

    public AgentTraceDetailDTO traceDetail(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            throw new BizException("追踪 ID 不能为空");
        }
        return traceRepo.findByTraceId(traceId.trim())
                .map(PluginDevToolsAssembler::toDetailDTO)
                .orElseThrow(() -> new BizException("执行追踪不存在或已过期：" + traceId));
    }

    public PluginCommandTestResult commandTest(String code, PluginCommandTestCmd cmd) {
        requireCode(code);
        if (cmd == null || !StringUtils.hasText(cmd.getCommand())) {
            throw new BizException("指令名不能为空");
        }
        return runtimeGateway.testCommand(code.trim(), cmd.getCommand(), cmd.getArguments(), cmd.getContent());
    }

    private void requireCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException("插件编码不能为空");
        }
    }
}
