package online.yudream.base.application.platform.seo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeoHeadInjectorTest {

    private static final String TEMPLATE = """
            <!DOCTYPE html>
            <html>
              <head>
                <meta charset="UTF-8" />
                <title>正在加载</title>
              </head>
              <body></body>
            </html>
            """;

    @Test
    void injectsMetaBlockAndReplacesTitle() {
        String html = SeoHeadInjector.inject(TEMPLATE,
                new SeoMeta("关于我们 - 站点", "站点介绍", "https://cdn.example.com/cover.png", false),
                "https://example.com/site/about");

        assertTrue(html.contains("<title>关于我们 - 站点</title>"));
        assertFalse(html.contains("正在加载"));
        assertTrue(html.contains("<meta name=\"description\" content=\"站点介绍\"/>"));
        assertTrue(html.contains("<meta name=\"robots\" content=\"index,follow,max-image-preview:large\"/>"));
        assertTrue(html.contains("<meta property=\"og:title\" content=\"关于我们 - 站点\"/>"));
        assertTrue(html.contains("<meta property=\"og:image\" content=\"https://cdn.example.com/cover.png\"/>"));
        assertTrue(html.contains("<link rel=\"canonical\" href=\"https://example.com/site/about\"/>"));
        // 注入块位于 head 内
        assertTrue(html.indexOf("<meta name=\"description\"") < html.indexOf("</head>"));
        assertTrue(html.indexOf("<title>关于我们") < html.indexOf("</head>"));
    }

    @Test
    void injectsNoindexForPrivatePath() {
        String html = SeoHeadInjector.inject(TEMPLATE,
                new SeoMeta("后台", null, null, true), null);

        assertTrue(html.contains("<meta name=\"robots\" content=\"noindex,nofollow\"/>"));
        assertFalse(html.contains("og:image"));
        assertFalse(html.contains("canonical"));
        assertFalse(html.contains("og:description"));
    }

    @Test
    void escapesHtmlInMetaValues() {
        String html = SeoHeadInjector.inject(TEMPLATE,
                new SeoMeta("标题<引号>\"&'", "描述&<\">", null, false), null);

        assertTrue(html.contains("<title>标题&lt;引号&gt;&quot;&amp;&#39;</title>"));
        assertTrue(html.contains("content=\"描述&amp;&lt;&quot;&gt;\""));
    }

    @Test
    void fallsBackWhenHeadMissing() {
        String html = SeoHeadInjector.inject("<html><body>x</body></html>",
                new SeoMeta("标题", null, null, false), null);

        assertTrue(html.startsWith("<title>标题</title>"));
        assertTrue(html.endsWith("<body>x</body></html>"));
    }

    @Test
    void keepsOriginalTemplateForNullMeta() {
        assertEquals(TEMPLATE, SeoHeadInjector.inject(TEMPLATE, null, null));
    }
}
