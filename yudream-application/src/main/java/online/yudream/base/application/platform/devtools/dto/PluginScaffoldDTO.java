package online.yudream.base.application.platform.devtools.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 插件脚手架生成结果：模块路径、入口类与已生成文件清单，registered 标记是否已同步登记为开发项目。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginScaffoldDTO {

    /** 插件编码 */
    private String code;

    /** 生成的模块根目录绝对路径 */
    private String projectPath;

    /** 入口类全限定名（plugin.yml 的 main） */
    private String mainClass;

    /** 写入 pom.xml 的 SPI 依赖版本 */
    private String spiVersion;

    /** 已生成文件清单（相对模块根目录） */
    private List<String> files;

    /** 是否已登记为开发模式项目（登记后执行一次 mvn compile 即被开发模式加载） */
    private boolean registered;
}
