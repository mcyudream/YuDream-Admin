package online.yudream.base.application.system.about.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.common.net.OutboundUrlGuard;
import online.yudream.base.application.system.about.assembler.AboutAppAssembler;
import online.yudream.base.application.system.about.dto.AboutLatestDTO;
import online.yudream.base.domain.system.about.service.LatestVersionGateway;
import online.yudream.base.domain.system.about.valobj.LatestVersionTarget;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 契约包最新版本探测编排：目标清单固定为平台契约（SPI + 三个 npm 契约包），
 * Nexus 基址可配置；探测失败由网关降级为单条错误结果，不影响整页。
 */
@Service
@RequiredArgsConstructor
public class AboutVersionCheckAppService {

    /** 探测目标：key 与前端当前版本注入的键一致（spi/plugin-sdk/components/dataviz）。 */
    private static final List<LatestVersionTarget> TARGETS = List.of(
            new LatestVersionTarget("spi", "Plugin SPI（插件契约）",
                    LatestVersionTarget.Kind.MAVEN, "online.yudream.base:yudream-plugin-spi"),
            new LatestVersionTarget("plugin-sdk", "@yudream/plugin-sdk",
                    LatestVersionTarget.Kind.NPM, "@yudream/plugin-sdk"),
            new LatestVersionTarget("components", "@yudream/components",
                    LatestVersionTarget.Kind.NPM, "@yudream/components"),
            new LatestVersionTarget("dataviz", "@yudream/dataviz",
                    LatestVersionTarget.Kind.NPM, "@yudream/dataviz"));

    private final LatestVersionGateway latestVersionGateway;

    @Value("${yudream.system.about.version-check.enabled:true}")
    private boolean enabled;

    @Value("${yudream.system.about.version-check.nexus-base-url:https://nexus.yudream.online}")
    private String nexusBaseUrl;

    public AboutLatestDTO latest() {
        if (!enabled) {
            return AboutLatestDTO.builder().enabled(false).entries(List.of()).build();
        }
        if (!StringUtils.hasText(nexusBaseUrl)) {
            return AboutLatestDTO.builder().enabled(true).entries(List.of()).build();
        }
        // 基址是部署级配置：出站前过 SSRF 守卫；允许私网以兼容内网 Nexus 部署
        OutboundUrlGuard.validate(nexusBaseUrl, "关于系统版本探测", true);
        return AboutLatestDTO.builder()
                .enabled(true)
                .entries(AboutAppAssembler.toLatestDTOs(latestVersionGateway.probe(TARGETS)))
                .build();
    }
}
