package online.yudream.base.domain.platform.wiki.valobj;

/**
 * 网络搜索结果（保留完整内容，不截断摘要）。
 */
public record WikiWebSearchResult(
        String title,
        String url,
        String snippet,
        String content
) {
}
