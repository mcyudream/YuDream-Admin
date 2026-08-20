package online.yudream.base.application.platform.devtools.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.service.AgentTraceProperties;
import online.yudream.base.application.platform.devtools.assembler.PluginDevToolsAssembler;
import online.yudream.base.application.platform.devtools.cmd.PluginCommandTestCmd;
import online.yudream.base.application.platform.devtools.cmd.PluginDevProjectSaveCmd;
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
import online.yudream.base.domain.platform.plugin.event.PluginDevReloadRequested;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandTestResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevDirectoryBrowseInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevProjectInfo;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    public PluginDevToolsStatusDTO status() {
        List<PluginModuleDTO> installed = pluginAppService.listInstalled();
        return PluginDevToolsStatusDTO.builder()
                .devModeEnabled(runtimeGateway.devModeEnabled())
                .traceEnabled(traceProperties.isEnabled())
                .hostRunMode(runtimeGateway.hostRunMode())
                .devModeAuto(runtimeGateway.devModeAutoDetected())
                .devProjectStoreFile(runtimeGateway.devProjectStoreFile())
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

    /** 开发项目管理视图：不受启用开关过滤，开发模式关闭时也可预登记。 */
    public List<PluginDevProjectInfo> devProjects() {
        return runtimeGateway.managedDevProjects();
    }

    public PluginDevProjectInfo addDevProject(PluginDevProjectSaveCmd cmd) {
        if (cmd == null || !StringUtils.hasText(cmd.getPath())) {
            throw new BizException("插件目录不能为空");
        }
        PluginDevProjectInfo saved = runtimeGateway.registerDevProject(cmd.getCode(), cmd.getPath().trim(),
                cmd.getFrontendDist(), cmd.getAutoCompile() == null || cmd.getAutoCompile(), cmd.getCompileCommand());
        // 插件已启用时立即切到源码目录加载，免去手动重载
        if (runtimeGateway.enabled(saved.code())) {
            eventPublisher.publishEvent(PluginDevReloadRequested.of(saved.code()));
        }
        return saved;
    }

    public void removeDevProject(String code) {
        requireCode(code);
        runtimeGateway.removeDevProject(code.trim());
    }

    /** 浏览宿主机目录供登记开发项目时选择插件源码目录；仅列目录与模块标记，不读文件内容。 */
    public PluginDevDirectoryBrowseInfo browseDevDirectories(String path) {
        return runtimeGateway.browseDevDirectories(path);
    }

    private void requireCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException("插件编码不能为空");
        }
    }
}
