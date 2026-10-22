package online.yudream.base.application.platform.workspace.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.workspace.cmd.WorkspaceRunCmd;
import online.yudream.base.application.platform.workspace.dto.WorkspaceRunResultDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.workspace.service.CodeRunnerGateway;
import online.yudream.base.domain.platform.workspace.valobj.CodeRunRequest;
import online.yudream.base.domain.platform.workspace.valobj.CodeRunResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 在代码运行器中执行工作区代码：按需把输入文件拷入运行器的临时目录，运行后把新建/改动的文件写入工作区的
 * 输出目录（缺省 tmp/runs/&lt;时间&gt;，随 tmp/ 定期清理），不覆盖原文件。运行器未部署或不可用时整体不可用。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class WorkspaceCodeRunAppService {

    static final int MAX_INPUT_FILES = 200;
    static final long MAX_INPUT_BYTES = 20L * 1024 * 1024;
    static final int DEFAULT_TIMEOUT_SECONDS = 30;
    static final int MAX_TIMEOUT_SECONDS = 120;
    static final int MEMORY_MB = 512;
    private static final Set<String> LANGUAGES = Set.of("python", "node");
    private static final DateTimeFormatter RUN_FOLDER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final ObjectProvider<CodeRunnerGateway> runners;

    /** 不可用原因；可用时返回 null */
    public String unavailableReason() {
        CodeRunnerGateway runner = runners.getIfAvailable();
        return runner == null ? "未部署代码运行器（yudream.platform.code-runner.enabled=false）" : runner.unavailableReason();
    }

    public Map<String, String> runtimes() {
        CodeRunnerGateway runner = runners.getIfAvailable();
        return runner == null ? Map.of() : runner.runtimes();
    }

    public WorkspaceRunResultDTO run(WorkspaceAccess access, WorkspaceRunCmd cmd) {
        CodeRunnerGateway runner = runners.getIfAvailable();
        String reason = unavailableReason();
        if (runner == null || reason != null) {
            throw new BizException(reason == null ? "代码运行器不可用" : reason);
        }
        String language = cmd.language() == null ? "" : cmd.language().trim().toLowerCase(Locale.ROOT);
        if (!LANGUAGES.contains(language)) {
            throw new BizException("language 只支持 python 或 node");
        }
        boolean hasCode = cmd.code() != null && !cmd.code().isBlank();
        String entry = cmd.entry() == null || cmd.entry().isBlank() ? null : WorkspacePaths.file(cmd.entry());
        if (!hasCode && entry == null) {
            throw new BizException("需要 entry（工作区中的入口文件）或 code（直接运行的代码）");
        }
        Map<String, byte[]> files = inputs(access, cmd.inputs(), hasCode ? null : entry);
        int timeout = cmd.timeoutSeconds() == null ? DEFAULT_TIMEOUT_SECONDS : Math.max(1, Math.min(cmd.timeoutSeconds(), MAX_TIMEOUT_SECONDS));
        CodeRunResult result = runner.run(new CodeRunRequest(language, entry, hasCode ? cmd.code() : null,
                cmd.args() == null ? List.of() : cmd.args().stream().limit(20).toList(), cmd.stdin(), Map.of(), files, timeout, MEMORY_MB));

        String outputDir = cmd.outputDir() == null || cmd.outputDir().isBlank()
                ? WorkspaceAppService.TMP_DIR + "/runs/" + LocalDateTime.now().format(RUN_FOLDER)
                : WorkspacePaths.directory(cmd.outputDir());
        if (outputDir.isEmpty()) {
            throw new BizException("输出目录不能是工作区根目录");
        }
        List<String> written = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        result.outputs().forEach((path, data) -> {
            String target;
            try {
                target = WorkspacePaths.join(outputDir, WorkspacePaths.file(path));
            } catch (BizException invalid) {
                failed.add(path + "（路径无效）");
                return;
            }
            try {
                written.add(access.writeBytes(target, data).path());
            } catch (BizException error) {
                failed.add(path + "（" + error.getMessage() + "）");
            }
        });
        return new WorkspaceRunResultDTO(result.exitCode(), result.timedOut(), result.durationMillis(), result.stdout(), result.stderr(),
                outputDir, written, failed, result.outputsTruncated());
    }

    /** 收集输入：路径可以是文件或目录（目录递归），保持工作区相对路径；入口文件自动加入 */
    private Map<String, byte[]> inputs(WorkspaceAccess access, List<String> requested, String entry) {
        List<String> paths = new ArrayList<>();
        for (String raw : requested == null ? List.<String>of() : requested) {
            String path = WorkspacePaths.directory(raw);
            if (path.isEmpty()) {
                throw new BizException("inputs 不能是整个工作区，请指定文件或目录");
            }
            List<String> inside = access.files(path);
            if (inside.isEmpty()) {
                paths.add(WorkspacePaths.file(path));
            } else {
                paths.addAll(inside);
            }
        }
        if (entry != null && !paths.contains(entry)) {
            paths.add(entry);
        }
        if (paths.size() > MAX_INPUT_FILES) {
            throw new BizException("一次最多带入 " + MAX_INPUT_FILES + " 个文件，请缩小 inputs 范围");
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        for (String path : paths) {
            byte[] data = access.readBytes(path);
            total += data.length;
            if (total > MAX_INPUT_BYTES) {
                throw new BizException("带入运行的文件合计不能超过 20 MB，请缩小 inputs 范围");
            }
            files.put(path, data);
        }
        return files;
    }
}
