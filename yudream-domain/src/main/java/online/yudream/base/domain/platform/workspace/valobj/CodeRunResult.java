package online.yudream.base.domain.platform.workspace.valobj;

import java.util.Map;

/** 运行结果：outputs 为运行中新建或改动的文件（相对路径 → 内容），超出回传上限时 outputsTruncated。 */
public record CodeRunResult(
        int exitCode,
        boolean timedOut,
        long durationMillis,
        String stdout,
        String stderr,
        Map<String, byte[]> outputs,
        boolean outputsTruncated
) {
    public CodeRunResult {
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }
}
