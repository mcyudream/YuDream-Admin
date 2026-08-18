package online.yudream.base.domain.platform.wiki.valobj;

import java.util.List;

/**
 * Lint 检查发现的一个问题。
 */
public record WikiLintIssue(
        String category,
        String severity,
        String title,
        String description,
        List<String> pageTitles,
        String suggestedAction,
        List<String> searchQueries
) {
    public static final String CATEGORY_CONTRADICTION = "contradiction";
    public static final String CATEGORY_STALE = "stale";
    public static final String CATEGORY_ORPHAN = "orphan";
    public static final String CATEGORY_MISSING_CROSS_REF = "missing_cross_ref";
    public static final String CATEGORY_MISSING_PAGE = "missing_page";
    public static final String CATEGORY_DATA_GAP = "data_gap";

    public WikiLintIssue {
        pageTitles = pageTitles == null ? List.of() : List.copyOf(pageTitles);
        searchQueries = searchQueries == null ? List.of() : List.copyOf(searchQueries);
    }
}
