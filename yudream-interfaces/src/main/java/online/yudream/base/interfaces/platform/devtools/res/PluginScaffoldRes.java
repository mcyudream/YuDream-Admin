package online.yudream.base.interfaces.platform.devtools.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 插件脚手架生成结果响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginScaffoldRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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

    /** 是否已登记为开发模式项目 */
    private boolean registered;
}
