package online.yudream.base.application.platform.devtools.cmd;

import lombok.Data;

import java.util.List;

/**
 * 新建插件骨架命令：在宿主机 parentDir 下生成 yudream-plugin-{code} Maven 模块。
 * register 为 true（默认）时生成后立即登记为开发模式项目。
 */
@Data
public class PluginScaffoldCmd {

    /** 目标父目录（宿主机绝对路径），模块目录 yudream-plugin-{code} 在其下创建 */
    private String parentDir;
    /** 插件编码，kebab-case，与 plugin.yml 的 name 一致 */
    private String code;
    /** 展示名，留空用编码 */
    private String displayName;
    private String description;
    /** 版本号，留空用 1.0.0 */
    private String version;
    /** 生成 pom 的 SPI 依赖版本，留空用宿主默认值 */
    private String spiVersion;
    /** 硬依赖插件编码（plugin.yml depend） */
    private List<String> depend;
    /** 软依赖插件编码（plugin.yml softdepend） */
    private List<String> softdepend;
    /** 生成后是否立即登记为开发模式项目 */
    private Boolean register = Boolean.TRUE;
}
