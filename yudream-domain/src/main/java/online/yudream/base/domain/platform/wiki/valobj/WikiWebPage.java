package online.yudream.base.domain.platform.wiki.valobj;

/**
 * 网页抓取结果。
 */
public record WikiWebPage(
        String title,
        String content,
        String contentType
) {
}
