package online.yudream.base.application.platform.seo;

import java.util.List;

/**
 * sitemap.xml 写出器：纯字符串处理，便于单测。
 */
public final class SeoSitemapWriter {

    /**
     * @param path    以 "/" 开头的站内路径
     * @param lastmod  ISO-8601 日期（yyyy-MM-dd），可空
     */
    public record UrlEntry(String path, String lastmod) {
    }

    private SeoSitemapWriter() {
    }

    public static String toXml(String siteUrl, List<UrlEntry> entries) {
        String base = siteUrl == null ? "" : siteUrl.trim().replaceAll("/+$", "");
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        if (entries != null) {
            for (UrlEntry entry : entries) {
                xml.append("  <url><loc>").append(escapeXml(base + entry.path())).append("</loc>");
                if (entry.lastmod() != null && !entry.lastmod().isBlank()) {
                    xml.append("<lastmod>").append(escapeXml(entry.lastmod())).append("</lastmod>");
                }
                xml.append("</url>\n");
            }
        }
        xml.append("</urlset>\n");
        return xml.toString();
    }

    private static String escapeXml(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
