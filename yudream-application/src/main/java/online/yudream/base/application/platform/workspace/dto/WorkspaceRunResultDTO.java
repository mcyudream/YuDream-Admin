package online.yudream.base.application.platform.workspace.dto;

import java.util.List;

/** 运行结果：产物已写入工作区 outputDir 下的 written；写入失败（如配额不足）的在 failed。 */
public record WorkspaceRunResultDTO(
        int exitCode,
        boolean timedOut,
        long durationMillis,
        String stdout,
        String stderr,
        String outputDir,
        List<String> written,
        List<String> failed,
        boolean outputsTruncated
) {
}
