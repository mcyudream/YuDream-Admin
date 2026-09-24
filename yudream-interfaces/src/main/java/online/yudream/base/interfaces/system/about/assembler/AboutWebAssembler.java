package online.yudream.base.interfaces.system.about.assembler;

import online.yudream.base.application.system.about.dto.AboutFrameworkDTO;
import online.yudream.base.application.system.about.dto.AboutLatestDTO;
import online.yudream.base.application.system.about.dto.AboutLatestVersionDTO;
import online.yudream.base.application.system.about.dto.AboutOverviewDTO;
import online.yudream.base.application.system.about.dto.AboutSpiDTO;
import online.yudream.base.application.system.about.dto.PluginGraphDTO;
import online.yudream.base.interfaces.system.about.res.AboutFrameworkRes;
import online.yudream.base.interfaces.system.about.res.AboutLatestRes;
import online.yudream.base.interfaces.system.about.res.AboutLatestVersionRes;
import online.yudream.base.interfaces.system.about.res.AboutOverviewRes;
import online.yudream.base.interfaces.system.about.res.AboutSpiRes;
import online.yudream.base.interfaces.system.about.res.PluginGraphRes;

import java.util.List;

/**
 * 关于系统页接口层装配：应用 DTO -> HTTP res。
 */
public final class AboutWebAssembler {

    private AboutWebAssembler() {
    }

    public static AboutOverviewRes toRes(AboutOverviewDTO dto) {
        return AboutOverviewRes.builder()
                .framework(toFrameworkRes(dto.getFramework()))
                .spi(toSpiRes(dto.getSpi()))
                .pluginTotal(dto.getPluginTotal())
                .pluginEnabled(dto.getPluginEnabled())
                .pluginError(dto.getPluginError())
                .build();
    }

    public static AboutLatestRes toRes(AboutLatestDTO dto) {
        List<AboutLatestVersionRes> entries = dto.getEntries().stream()
                .map(AboutWebAssembler::toVersionRes)
                .toList();
        return AboutLatestRes.builder().enabled(dto.isEnabled()).entries(entries).build();
    }

    public static PluginGraphRes toRes(PluginGraphDTO dto) {
        List<PluginGraphRes.PluginGraphNode> nodes = dto.getNodes().stream()
                .map(node -> PluginGraphRes.PluginGraphNode.builder()
                        .code(node.getCode())
                        .name(node.getName())
                        .version(node.getVersion())
                        .status(node.getStatus())
                        .errorMessage(node.getErrorMessage())
                        .build())
                .toList();
        List<PluginGraphRes.PluginGraphEdge> edges = dto.getEdges().stream()
                .map(edge -> PluginGraphRes.PluginGraphEdge.builder()
                        .source(edge.getSource())
                        .target(edge.getTarget())
                        .kind(edge.getKind())
                        .build())
                .toList();
        return PluginGraphRes.builder().nodes(nodes).edges(edges).build();
    }

    private static AboutFrameworkRes toFrameworkRes(AboutFrameworkDTO dto) {
        if (dto == null) {
            return null;
        }
        return AboutFrameworkRes.builder()
                .name(dto.getName())
                .version(dto.getVersion())
                .buildTime(dto.getBuildTime())
                .springBootVersion(dto.getSpringBootVersion())
                .javaVersion(dto.getJavaVersion())
                .osName(dto.getOsName())
                .osArch(dto.getOsArch())
                .build();
    }

    private static AboutSpiRes toSpiRes(AboutSpiDTO dto) {
        if (dto == null) {
            return null;
        }
        return AboutSpiRes.builder()
                .version(dto.getVersion())
                .artifact(dto.getArtifact())
                .buildTime(dto.getBuildTime())
                .build();
    }

    private static AboutLatestVersionRes toVersionRes(AboutLatestVersionDTO dto) {
        return AboutLatestVersionRes.builder()
                .key(dto.getKey())
                .name(dto.getName())
                .kind(dto.getKind())
                .latest(dto.getLatest())
                .sourceUrl(dto.getSourceUrl())
                .checkedAt(dto.getCheckedAt())
                .error(dto.getError())
                .build();
    }
}
