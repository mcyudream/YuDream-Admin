package online.yudream.base.application.platform.seo;

import online.yudream.base.application.platform.cms.dto.CmsPageDTO;
import online.yudream.base.application.platform.wiki.dto.WikiNodeDTO;
import online.yudream.base.application.platform.wiki.dto.WikiPublicSpaceDTO;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketPublication;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeoViewAppServiceTest {

    @TempDir
    Path tempDir;

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
    void rendersSiteMetaForRoot() throws Exception {
        SeoViewAppService.SeoSite site = new SeoViewAppService.SeoSite("站点名", "站点描述", "");
        SeoViewAppService service = new SeoViewAppService(null, null, null, null) {
            @Override
            protected SeoSite loadSiteMeta() {
                return site;
            }
        };
        configure(service, template());

        String html = service.renderView("/");

        assertTrue(html.contains("<title>站点名</title>"));
        assertTrue(html.contains("<meta name=\"description\" content=\"站点描述\"/>"));
        assertTrue(html.contains("<meta name=\"robots\" content=\"index,follow,max-image-preview:large\"/>"));
        assertTrue(html.contains("<link rel=\"canonical\" href=\"https://example.com/\"/>"));
        assertFalse(html.contains("noindex"));
    }

    @Test
    void injectsNoindexAndSiteTitleForAdminPath() throws Exception {
        SeoViewAppService.SeoSite site = new SeoViewAppService.SeoSite("站点名", "站点描述", "");
        SeoViewAppService service = new SeoViewAppService(null, null, null, null) {
            @Override
            protected SeoSite loadSiteMeta() {
                return site;
            }
        };
        configure(service, template());

        String html = service.renderView("/platform/plugins/feedback/me/feedbacks/new");

        assertTrue(html.contains("<title>站点名</title>"));
        assertTrue(html.contains("<meta name=\"robots\" content=\"noindex,nofollow\"/>"));
    }

    @Test
    void rendersCmsPageSeoMeta() throws Exception {
        SeoViewAppService.SeoSite site = new SeoViewAppService.SeoSite("站点名", "站点描述", "/logo.png");
        CmsPageDTO page = CmsPageDTO.builder()
                .title("页面标题")
                .summary("页面摘要")
                .coverImageUrl("/api/system/file/cover.png")
                .seoTitle("页面SEO标题")
                .seoDescription("页面SEO描述")
                .build();
        SeoViewAppService service = new SeoViewAppService(null, null, null, null) {
            @Override
            protected SeoSite loadSiteMeta() {
                return site;
            }

            @Override
            protected Optional<CmsPageDTO> findCmsPage(String slug) {
                assertEquals("about", slug);
                return Optional.of(page);
            }
        };
        configure(service, template());

        String html = service.renderView("/site/about");

        assertTrue(html.contains("<title>页面SEO标题</title>"));
        assertTrue(html.contains("<meta name=\"description\" content=\"页面SEO描述\"/>"));
        assertTrue(html.contains("<meta property=\"og:image\" content=\"https://example.com/api/system/file/cover.png\"/>"));
        assertTrue(html.contains("<link rel=\"canonical\" href=\"https://example.com/site/about\"/>"));
    }

    @Test
    void fallsBackWhenCmsPageMissing() throws Exception {
        SeoViewAppService.SeoSite site = new SeoViewAppService.SeoSite("站点名", "站点描述", "");
        SeoViewAppService service = new SeoViewAppService(null, null, null, null) {
            @Override
            protected SeoSite loadSiteMeta() {
                return site;
            }

            @Override
            protected Optional<CmsPageDTO> findCmsPage(String slug) {
                return Optional.empty();
            }
        };
        configure(service, template());

        String html = service.renderView("/site/missing");

        assertTrue(html.contains("<title>内容站点 - 站点名</title>"));
    }

    @Test
    void rendersWikiSpaceAndDocMeta() throws Exception {
        SeoViewAppService.SeoSite site = new SeoViewAppService.SeoSite("站点名", "站点描述", "");
        SeoViewAppService service = new SeoViewAppService(null, null, null, null) {
            @Override
            protected SeoSite loadSiteMeta() {
                return site;
            }

            @Override
            protected List<WikiPublicSpaceDTO> wikiSpaces() {
                return List.of(new WikiPublicSpaceDTO("入门空间", "intro", "新手入门"));
            }

            @Override
            protected List<WikiNodeDTO> wikiTree(String spaceSlug) {
                WikiNodeDTO doc = WikiNodeDTO.builder().title("安装指南").path("start/install").nodeType(WikiNodeType.PAGE).build();
                return List.of(WikiNodeDTO.builder().title("开始").path("start").nodeType(WikiNodeType.DIRECTORY).children(List.of(doc)).build());
            }
        };
        configure(service, template());

        String spaceHtml = service.renderView("/wiki/intro");
        assertTrue(spaceHtml.contains("<title>入门空间 - 站点名</title>"));
        assertTrue(spaceHtml.contains("<meta name=\"description\" content=\"新手入门\"/>"));

        String docHtml = service.renderView("/wiki/intro/start/install");
        assertTrue(docHtml.contains("<title>安装指南 - 站点名</title>"));
    }

    @Test
    void rendersMarketDetailMeta() throws Exception {
        SeoViewAppService.SeoSite site = new SeoViewAppService.SeoSite("站点名", "站点描述", "");
        SeoViewAppService service = new SeoViewAppService(null, null, null, null) {
            @Override
            protected SeoSite loadSiteMeta() {
                return site;
            }

            @Override
            protected List<PluginMarketPublication> marketPublished() {
                PluginMarketPublication item = new PluginMarketPublication();
                item.setCode("mc-wiki");
                item.setDisplayName("MC 百科");
                item.setDescription("Minecraft 知识库插件");
                return List.of(item);
            }
        };
        configure(service, template());

        String html = service.renderView("/market/mc-wiki");

        assertTrue(html.contains("<title>MC 百科 - 站点名</title>"));
        assertTrue(html.contains("<meta name=\"description\" content=\"Minecraft 知识库插件\"/>"));
    }

    @Test
    void returnsNullWhenTemplateMissing() throws Exception {
        SeoViewAppService.SeoSite site = new SeoViewAppService.SeoSite("站点名", "", "");
        SeoViewAppService service = new SeoViewAppService(null, null, null, null) {
            @Override
            protected SeoSite loadSiteMeta() {
                return site;
            }
        };
        configure(service, null);

        assertNull(service.renderView("/"));
    }

    @Test
    void buildsSitemapFromPublicSources() throws Exception {
        SeoViewAppService.SeoSite site = new SeoViewAppService.SeoSite("站点名", "", "");
        SeoViewAppService service = new SeoViewAppService(null, null, null, null) {
            @Override
            protected SeoSite loadSiteMeta() {
                return site;
            }

            @Override
            protected List<WikiPublicSpaceDTO> wikiSpaces() {
                return List.of(new WikiPublicSpaceDTO("入门空间", "intro", ""));
            }

            @Override
            protected List<WikiNodeDTO> wikiTree(String spaceSlug) {
                WikiNodeDTO doc = WikiNodeDTO.builder().title("安装指南").path("start/install").nodeType(WikiNodeType.PAGE).build();
                return List.of(WikiNodeDTO.builder().title("开始").path("start").nodeType(WikiNodeType.DIRECTORY).children(List.of(doc)).build());
            }

            @Override
            protected List<PluginMarketPublication> marketPublished() {
                PluginMarketPublication item = new PluginMarketPublication();
                item.setCode("mc-wiki");
                return List.of(item);
            }
        };
        configure(service, template());

        String xml = service.sitemapXml();

        assertTrue(xml.startsWith("<?xml"));
        assertTrue(xml.contains("<loc>https://example.com/</loc>"));
        assertTrue(xml.contains("<loc>https://example.com/site</loc>"));
        assertTrue(xml.contains("<loc>https://example.com/wiki/intro</loc>"));
        assertTrue(xml.contains("<loc>https://example.com/wiki/intro/start/install</loc>"));
        assertTrue(xml.contains("<loc>https://example.com/market/mc-wiki</loc>"));
        assertFalse(xml.contains("/embed"));
        assertEquals(xml, service.sitemapXml());
    }

    private String template() throws Exception {
        Path file = tempDir.resolve("index.html");
        Files.writeString(file, TEMPLATE);
        return file.toString();
    }

    private void configure(SeoViewAppService service, String templatePath) throws Exception {
        setField(service, "indexHtmlPath", templatePath);
        setField(service, "siteUrl", "https://example.com");
        setField(service, "publicPrefixes", "/site,/wiki,/market,/servers,/activities,/timeline,/forms,/embed");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = SeoViewAppService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
