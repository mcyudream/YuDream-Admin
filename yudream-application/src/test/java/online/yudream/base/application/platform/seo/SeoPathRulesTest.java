package online.yudream.base.application.platform.seo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeoPathRulesTest {

    @Test
    void normalizesPathVariants() {
        assertEquals("/", SeoPathRules.normalizePath(null));
        assertEquals("/", SeoPathRules.normalizePath(""));
        assertEquals("/", SeoPathRules.normalizePath("/"));
        assertEquals("/site/about", SeoPathRules.normalizePath("/site/about?utm=x#top"));
        assertEquals("/site/about", SeoPathRules.normalizePath("//site//about/"));
        assertEquals("/wiki/入门/安装", SeoPathRules.normalizePath("/wiki/入门/安装/"));
        assertEquals("/login", SeoPathRules.normalizePath("login"));
    }

    @Test
    void classifiesPublicPaths() {
        List<String> prefixes = List.of("/site", "/wiki", "/market");
        assertTrue(SeoPathRules.isPublicPath("/", prefixes));
        assertTrue(SeoPathRules.isPublicPath("/site", prefixes));
        assertTrue(SeoPathRules.isPublicPath("/site/about", prefixes));
        assertTrue(SeoPathRules.isPublicPath("/market/code", prefixes));
        assertFalse(SeoPathRules.isPublicPath("/marketplace-like", prefixes));
        assertFalse(SeoPathRules.isPublicPath("/platform/plugins/x", prefixes));
        assertFalse(SeoPathRules.isPublicPath("/login", prefixes));
        assertFalse(SeoPathRules.isPublicPath("/site", null));
    }

    @Test
    void extractsSubPath() {
        assertEquals("", SeoPathRules.subPath("/site", "/site"));
        assertEquals("about", SeoPathRules.subPath("/site/about", "/site"));
        assertEquals("about/team", SeoPathRules.subPath("/site/about/team", "/site"));
        assertEquals("space/doc/a", SeoPathRules.subPath("/wiki/space/doc/a", "/wiki"));
    }

    @Test
    void parsesPrefixCsv() {
        assertEquals(List.of("/site", "/wiki", "/servers"),
                SeoPathRules.parsePrefixes(" /site, wiki ,servers,"));
        assertEquals(List.of(), SeoPathRules.parsePrefixes(" "));
        assertEquals(List.of(), SeoPathRules.parsePrefixes(null));
    }
}
