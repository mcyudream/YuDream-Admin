package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

/**
 * 插件脚手架生成结果快照。
 * projectPath 为生成的模块根目录绝对路径；files 为相对模块根目录的已生成文件清单；
 * spiVersion 为写入 pom.xml 的 SPI 依赖版本（请求未指定时取宿主默认值）。
 */
public record PluginScaffoldResult(
        String code,
        String projectPath,
        String mainClass,
        String spiVersion,
        List<String> files
) {
}
