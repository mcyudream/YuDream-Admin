package online.yudream.base.application.platform.devtools.cmd;

import lombok.Data;

/**
 * 登记开发模式插件项目命令：path 必填；code 为空时由后端从目录内 plugin.yml 推断。
 */
@Data
public class PluginDevProjectSaveCmd {

    /** 插件模块根目录（含 pom.xml 的目录） */
    private String path;
    /** 插件 code，留空时从 target/classes/plugin.yml 或 src/main/resources/plugin.yml 推断 */
    private String code;
    /** 前端产物目录，留空按官方插件仓布局推导 */
    private String frontendDist;
    /** 监听到源码变化时是否自动执行编译命令 */
    private Boolean autoCompile = Boolean.TRUE;
    /** 编译命令，留空用默认 mvn -q compile -DskipTests */
    private String compileCommand;
}
