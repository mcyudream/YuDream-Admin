package online.yudream.base.application.platform.workspace.cmd;

import java.util.List;

/**
 * 在代码运行器中执行：language 为 python / node；entry 为工作区中的入口文件，或给 code 直接运行；
 * inputs 为一并放入运行目录的工作区文件或目录；产物写入 outputDir（缺省 tmp/runs/&lt;时间&gt;）。
 */
public record WorkspaceRunCmd(
        String language,
        String entry,
        String code,
        List<String> inputs,
        List<String> args,
        String stdin,
        String outputDir,
        Integer timeoutSeconds
) {
}
