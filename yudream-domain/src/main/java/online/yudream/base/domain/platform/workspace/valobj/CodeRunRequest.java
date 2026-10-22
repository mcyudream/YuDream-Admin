package online.yudream.base.domain.platform.workspace.valobj;

import java.util.List;
import java.util.Map;

/**
 * 一次代码运行：language 为 python / node；entry 为输入文件中的入口，或给 code 直接运行（写为 main.py / main.js）。
 * files 为放入运行目录的文件（相对路径 → 内容）。
 */
public record CodeRunRequest(
        String language,
        String entry,
        String code,
        List<String> args,
        String stdin,
        Map<String, String> env,
        Map<String, byte[]> files,
        int timeoutSeconds,
        int memoryMb
) {
    public CodeRunRequest {
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : Map.copyOf(env);
        files = files == null ? Map.of() : Map.copyOf(files);
    }
}
