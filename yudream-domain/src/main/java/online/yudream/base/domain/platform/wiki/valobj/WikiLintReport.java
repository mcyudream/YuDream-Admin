package online.yudream.base.domain.platform.wiki.valobj;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一次 Lint 检查的完整报告。
 */
public record WikiLintReport(
        LocalDateTime generatedAt,
        String summary,
        List<WikiLintIssue> issues
) {
    public WikiLintReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
