package online.yudream.base.application.platform.seo;

/**
 * 把 SEO 元数据注入静态壳 index.html 的 head：纯字符串处理，便于单测。
 * 注入后静态壳原有的「正在加载」标题被替换，爬虫与链接预览拿到真实标题与摘要。
 */
public final class SeoHeadInjector {

    private SeoHeadInjector() {
    }

    /**
     * @param template     前端构建产物 index.html 内容
     * @param meta         路由元数据
     * @param canonicalUrl 规范地址（站点地址 + 路由路径），可空
     * @return 注入后的 HTML；模板缺少 head 时退化为在文档开头前置元数据块
     */
    public static String inject(String template, SeoMeta meta, String canonicalUrl) {
        if (template == null || template.isBlank() || meta == null) {
            return template;
        }
        StringBuilder block = new StringBuilder();
        String title = hasText(meta.title()) ? meta.title() : "正在加载";
        block.append("<title>").append(escapeHtml(title)).append("</title>");
        if (hasText(meta.description())) {
            block.append("<meta name=\"description\" content=\"").append(escapeHtml(meta.description())).append("\"/>");
        }
        block.append(meta.noindex()
                ? "<meta name=\"robots\" content=\"noindex,nofollow\"/>"
                : "<meta name=\"robots\" content=\"index,follow,max-image-preview:large\"/>");
        block.append("<meta property=\"og:type\" content=\"website\"/>");
        block.append("<meta property=\"og:title\" content=\"").append(escapeHtml(title)).append("\"/>");
        if (hasText(meta.description())) {
            block.append("<meta property=\"og:description\" content=\"").append(escapeHtml(meta.description())).append("\"/>");
        }
        if (hasText(meta.image())) {
            block.append("<meta property=\"og:image\" content=\"").append(escapeHtml(meta.image())).append("\"/>");
        }
        if (hasText(canonicalUrl)) {
            block.append("<meta property=\"og:url\" content=\"").append(escapeHtml(canonicalUrl)).append("\"/>");
            block.append("<link rel=\"canonical\" href=\"").append(escapeHtml(canonicalUrl)).append("\"/>");
        }
        String withoutTitle = template.replaceFirst("(?s)<title>[^<]*</title>", "");
        int headIndex = withoutTitle.indexOf("<head>");
        if (headIndex < 0) {
            return block + withoutTitle;
        }
        return withoutTitle.substring(0, headIndex + "<head>".length())
                + block
                + withoutTitle.substring(headIndex + "<head>".length());
    }

    static String escapeHtml(String value) {
        String safe = value == null ? "" : value;
        StringBuilder escaped = new StringBuilder(safe.length());
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
