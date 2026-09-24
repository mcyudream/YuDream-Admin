package online.yudream.base.application.system.about.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.about.assembler.AboutAppAssembler;
import online.yudream.base.application.system.about.dto.AboutOverviewDTO;
import online.yudream.base.application.system.about.dto.PluginGraphDTO;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import online.yudream.base.domain.platform.plugin.repo.PluginModuleRepo;
import online.yudream.base.domain.system.about.service.AboutBuildInfoGateway;
import online.yudream.base.domain.system.about.service.SpiBuildInfoGateway;
import online.yudream.base.domain.system.about.valobj.AboutBuildInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 关于系统页：框架/SPI 当前版本与插件装载统计。全部数据来自本地，不触发任何出站请求。
 */
@Service
@RequiredArgsConstructor
public class AboutAppService {

    private final PluginModuleRepo pluginModuleRepo;
    private final SpiBuildInfoGateway spiBuildInfoGateway;
    private final AboutBuildInfoGateway aboutBuildInfoGateway;

    public AboutOverviewDTO overview() {
        List<PluginModule> modules = pluginModuleRepo.findAll();
        int enabled = 0;
        int error = 0;
        for (PluginModule module : modules) {
            if (module.getStatus() == PluginStatus.ENABLED) {
                enabled++;
            } else if (module.getStatus() == PluginStatus.ERROR) {
                error++;
            }
        }
        AboutBuildInfo buildInfo = aboutBuildInfoGateway.read().orElse(null);
        return AboutOverviewDTO.builder()
                .framework(AboutAppAssembler.toFrameworkDTO(
                        buildInfo == null ? null : buildInfo.version(),
                        buildInfo == null ? null : buildInfo.buildTime()))
                .spi(AboutAppAssembler.toSpiDTO(spiBuildInfoGateway.read().orElse(null)))
                .pluginTotal(modules.size())
                .pluginEnabled(enabled)
                .pluginError(error)
                .build();
    }

    public PluginGraphDTO pluginGraph() {
        return AboutAppAssembler.toPluginGraphDTO(pluginModuleRepo.findAll());
    }
}
