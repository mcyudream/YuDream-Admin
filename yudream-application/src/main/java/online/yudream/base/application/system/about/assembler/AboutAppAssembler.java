package online.yudream.base.application.system.about.assembler;

import online.yudream.base.application.system.about.dto.AboutFrameworkDTO;
import online.yudream.base.application.system.about.dto.AboutLatestVersionDTO;
import online.yudream.base.application.system.about.dto.AboutOverviewDTO;
import online.yudream.base.application.system.about.dto.AboutSpiDTO;
import online.yudream.base.application.system.about.dto.PluginGraphEdgeDTO;
import online.yudream.base.application.system.about.dto.PluginGraphNodeDTO;
import online.yudream.base.application.system.about.dto.PluginGraphDTO;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import online.yudream.base.domain.system.about.valobj.LatestVersionProbe;
import online.yudream.base.domain.system.about.valobj.SpiBuildInfo;
import org.springframework.boot.SpringBootVersion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 关于系统页的应用层装配：领域/运行时信息 -> 应用 DTO。
 */
public final class AboutAppAssembler {

    private AboutAppAssembler() {
    }

    public static AboutFrameworkDTO toFrameworkDTO(String version, String buildTime) {
        // 版本为空表示构建期未注入（IDE 源码运行）：原样返回空，由前端呈现「源码运行」，
        // 不要在这里兜底成 "unknown" 之类的真值字符串，否则前端拿不到空值、无法给出准确提示
        return AboutFrameworkDTO.builder()
                .name("YuDream Admin")
                .version(version == null || version.isBlank() ? null : version)
                .buildTime(buildTime)
                .springBootVersion(SpringBootVersion.getVersion())
                .javaVersion(System.getProperty("java.version"))
                .osName(System.getProperty("os.name"))
                .osArch(System.getProperty("os.arch"))
                .build();
    }

    public static AboutSpiDTO toSpiDTO(SpiBuildInfo info) {
        if (info == null) {
            return AboutSpiDTO.builder().build();
        }
        return AboutSpiDTO.builder()
                .version(info.version())
                .artifact(info.artifact())
                .buildTime(info.buildTime())
                .build();
    }

    public static List<AboutLatestVersionDTO> toLatestDTOs(List<LatestVersionProbe> probes) {
        List<AboutLatestVersionDTO> result = new ArrayList<>();
        for (LatestVersionProbe probe : probes) {
            result.add(AboutLatestVersionDTO.builder()
                    .key(probe.key())
                    .name(probe.name())
                    .kind(probe.kind().name())
                    .latest(probe.latest())
                    .sourceUrl(probe.sourceUrl())
                    .checkedAt(probe.checkedAt())
                    .error(probe.error())
                    .build());
        }
        return result;
    }

    /** 插件依赖图：依赖方 -> 被依赖方；引用到已删除插件的悬空依赖保留为独立灰色节点。 */
    public static PluginGraphDTO toPluginGraphDTO(List<PluginModule> modules) {
        List<PluginGraphNodeDTO> nodes = new ArrayList<>();
        List<PluginGraphEdgeDTO> edges = new ArrayList<>();
        Set<String> knownCodes = new LinkedHashSet<>();
        for (PluginModule module : modules) {
            knownCodes.add(module.getCode());
        }
        Set<String> nodeCodes = new LinkedHashSet<>(knownCodes);
        for (PluginModule module : modules) {
            nodes.add(PluginGraphNodeDTO.builder()
                    .code(module.getCode())
                    .name(module.getName())
                    .version(module.getPluginVersion())
                    .status(module.getStatus() == null ? PluginStatus.INSTALLED.name() : module.getStatus().name())
                    .errorMessage(module.getErrorMessage())
                    .build());
            for (String dependency : safeList(module.getDependencies())) {
                if (nodeCodes.add(dependency)) {
                    nodes.add(ghostNode(dependency));
                }
                edges.add(PluginGraphEdgeDTO.builder()
                        .source(module.getCode()).target(dependency).kind("HARD").build());
            }
            for (String dependency : safeList(module.getSoftDependencies())) {
                if (nodeCodes.add(dependency)) {
                    nodes.add(ghostNode(dependency));
                }
                edges.add(PluginGraphEdgeDTO.builder()
                        .source(module.getCode()).target(dependency).kind("SOFT").build());
            }
        }
        return PluginGraphDTO.builder().nodes(List.copyOf(nodes)).edges(List.copyOf(edges)).build();
    }

    private static PluginGraphNodeDTO ghostNode(String code) {
        return PluginGraphNodeDTO.builder()
                .code(code)
                .name(code)
                .status("MISSING")
                .errorMessage("被依赖但未安装")
                .build();
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
