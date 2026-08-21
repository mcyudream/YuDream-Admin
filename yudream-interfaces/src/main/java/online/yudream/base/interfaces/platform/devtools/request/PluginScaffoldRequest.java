package online.yudream.base.interfaces.platform.devtools.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 新建插件骨架请求。
 */
@Data
public class PluginScaffoldRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标父目录（宿主机绝对路径），模块目录 yudream-plugin-{code} 在其下创建 */
    @NotBlank(message = "目标父目录不能为空")
    private String parentDir;

    /** 插件编码，kebab-case，与 plugin.yml 的 name 一致 */
    @NotBlank(message = "插件编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9]*(-[a-z0-9]+)*$", message = "插件编码需为小写字母开头的 kebab-case")
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

    /** 生成后是否立即登记为开发模式项目，缺省开启 */
    private Boolean register;
}
