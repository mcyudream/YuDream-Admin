package online.yudream.base.interfaces.platform.devtools.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登记开发模式插件项目请求。
 */
@Data
public class PluginDevProjectSaveRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 插件模块根目录（含 pom.xml 的目录） */
    @NotBlank(message = "插件目录不能为空")
    private String path;

    /** 插件 code，留空时由后端从目录内 plugin.yml 推断 */
    private String code;

    /** 前端产物目录，留空按官方插件仓布局推导 */
    private String frontendDist;

    /** 监听到源码变化时是否自动执行编译命令，缺省开启 */
    private Boolean autoCompile;

    /** 编译命令，留空用默认 mvn -q compile -DskipTests */
    private String compileCommand;
}
