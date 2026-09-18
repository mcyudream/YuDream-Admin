package online.yudream.base.application.platform.seo;

import online.yudream.base.application.platform.cms.dto.CmsPageDTO;
import online.yudream.base.application.platform.cms.query.CmsPageQuery;
import online.yudream.base.application.platform.cms.service.CmsAppService;
import online.yudream.base.application.platform.wiki.dto.WikiNodeDTO;
import online.yudream.base.application.platform.wiki.dto.WikiPublicSpaceDTO;
import online.yudream.base.application.platform.wiki.service.WikiPublicAppService;
import online.yudream.base.application.system.setting.service.SettingAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketPublication;
import online.yudream.base.domain.platform.plugin.enumerate.PluginPublicationStatus;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketPublicationRepo;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 公开页 SEO：读取前端静态壳 index.html，按路由注入真实标题/摘要/og/canonical/robots，
 * 供不执行 JS 的抓取方（百度、微信/QQ 链接预览）使用；并基于同一批公开数据生成 sitemap。
 * 任一数据源不可用时按空数据降级，绝不阻塞页面壳返回。
 */
@Service
public class SeoViewAppService {

    private static final Logger log = LoggerFactory.getLogger(SeoViewAppService.class);

    private static final String DEFAULT_PUBLIC_PREFIXES = "/site,/wiki,/market,/servers,/activities,/timeline,/forms,/embed";
    private static final long SITEMAP_CACHE_MILLIS = 5 * 60 * 1000L;
    private static final DateTimeFormatter SITEMAP_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 无宿主数据源的插件公开页分区标题（/servers 等由 minecraft-server 等插件贡献路由）。
     */
    private static final Map<String, String> PUBLIC_SECTION_LABELS = Map.of(
            "/servers", "服务器",
            "/activities", "活动",
            "/timeline", "时间轴",
            "/forms", "表单填写",
            "/embed", "嵌入页面"
    );

    private final SettingAppService settingAppService;
    private final CmsAppService cmsAppService;
    private final WikiPublicAppService wikiPublicAppService;
    private final PluginMarketPublicationRepo marketPublicationRepo;

    @Value("${yudream.seo.index-html-path:}")
    private String indexHtmlPath;

    @Value("${yudream.seo.site-url:}")
    private String siteUrl;

    @Value("${yudream.seo.public-prefixes:" + DEFAULT_PUBLIC_PREFIXES + "}")
    private String publicPrefixes;

    private final AtomicReference<TemplateSnapshot> templateCache = new AtomicReference<>();
    private volatile String sitemapCache;
    private volatile long sitemapCacheAt;

    public SeoViewAppService(SettingAppService settingAppService,
                             CmsAppService cmsAppService,
                             WikiPublicAppService wikiPublicAppService,
                             PluginMarketPublicationRepo marketPublicationRepo) {
        this.settingAppService = settingAppService;
        this.cmsAppService = cmsAppService;
        this.wikiPublicAppService = wikiPublicAppService;
        this.marketPublicationRepo = marketPublicationRepo;
    }

    /**
     * 渲染注入 SEO 元数据后的页面壳；模板未配置或不可读时返回 null（接口层转 404，nginx 回落静态壳）。
     */
    public String renderView(String rawPath) {
        String template = loadTemplate();
        if (template == null) {
            return null;
        }
        String path = SeoPathRules.normalizePath(rawPath);
        SeoMeta meta = resolveMeta(path);
        return SeoHeadInjector.inject(template, meta, canonicalUrl(path));
    }

    /**
     * 公开内容 sitemap，5 分钟内存缓存。
     */
    public String sitemapXml() {
        long now = System.currentTimeMillis();
        String cached = sitemapCache;
        if (cached != null && now - sitemapCacheAt < SITEMAP_CACHE_MILLIS) {
            return cached;
        }
        synchronized (this) {
            long refreshed = System.currentTimeMillis();
            if (sitemapCache != null && refreshed - sitemapCacheAt < SITEMAP_CACHE_MILLIS) {
                return sitemapCache;
            }
            String xml = SeoSitemapWriter.toXml(siteUrl(), collectSitemapEntries());
            sitemapCache = xml;
            sitemapCacheAt = refreshed;
            return xml;
        }
    }

    private SeoMeta resolveMeta(String path) {
        SeoSite site = loadSiteMeta();
        if ("/".equals(path)) {
            return new SeoMeta(site.name(), site.description(), site.logoUrl(), false);
        }
        if (!SeoPathRules.isPublicPath(path, SeoPathRules.parsePrefixes(publicPrefixes))) {
            // 后台/认证路径对搜索引擎关闭：注入 noindex，标题给站点名避免 tab 出现「正在加载」
            return new SeoMeta(site.name(), null, null, true);
        }
        if ("/site".equals(path)) {
            return new SeoMeta(joinTitle("内容站点", site.name()), site.description(), site.logoUrl(), false);
        }
        if (path.startsWith("/site/")) {
            String slug = SeoPathRules.subPath(path, "/site");
            return findCmsPage(slug)
                    .map(page -> new SeoMeta(
                            firstText(page.getSeoTitle(), page.getTitle(), site.name()),
                            firstText(page.getSeoDescription(), page.getSummary(), site.description()),
                            firstText(resolveAsset(page.getCoverImageUrl()), site.logoUrl()),
                            false))
                    .orElseGet(() -> new SeoMeta(joinTitle("内容站点", site.name()), site.description(), null, false));
        }
        if ("/wiki".equals(path)) {
            return new SeoMeta(joinTitle("知识库", site.name()), site.description(), null, false);
        }
        if (path.startsWith("/wiki/")) {
            return resolveWikiMeta(path, site);
        }
        if ("/market".equals(path)) {
            return new SeoMeta(joinTitle("插件市场", site.name()), site.description(), null, false);
        }
        if (path.startsWith("/market/")) {
            String code = SeoPathRules.subPath(path, "/market");
            return marketPublished().stream()
                    .filter(item -> code.equals(item.getCode()))
                    .findFirst()
                    .map(item -> new SeoMeta(
                            joinTitle(item.getDisplayName(), site.name()),
                            firstText(item.getDescription(), site.description()),
                            null,
                            false))
                    .orElseGet(() -> new SeoMeta(joinTitle("插件市场", site.name()), site.description(), null, false));
        }
        String label = PUBLIC_SECTION_LABELS.entrySet().stream()
                .filter(entry -> path.equals(entry.getKey()) || path.startsWith(entry.getKey() + "/"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(site.name());
        return new SeoMeta(joinTitle(label, site.name()), site.description(), null, false);
    }

    private SeoMeta resolveWikiMeta(String path, SeoSite site) {
        String rest = SeoPathRules.subPath(path, "/wiki");
        String[] segments = rest.split("/");
        String spaceSlug = segments[0];
        Optional<WikiPublicSpaceDTO> space = wikiSpaces().stream()
                .filter(item -> spaceSlug.equals(item.slug()))
                .findFirst();
        if (segments.length == 1) {
            return space
                    .map(item -> new SeoMeta(
                            joinTitle(item.name(), site.name()),
                            firstText(item.description(), site.description()),
                            null,
                            false))
                    .orElseGet(() -> new SeoMeta(joinTitle("知识库", site.name()), site.description(), null, false));
        }
        String nodePath = rest.substring(spaceSlug.length() + 1);
        String docTitle = findWikiDocTitle(spaceSlug, nodePath);
        return space
                .map(item -> new SeoMeta(
                        joinTitle(docTitle != null ? docTitle : item.name(), site.name()),
                        firstText(docTitle != null ? item.description() : null, site.description()),
                        null,
                        false))
                .orElseGet(() -> new SeoMeta(joinTitle("知识库", site.name()), site.description(), null, false));
    }

    private List<SeoSitemapWriter.UrlEntry> collectSitemapEntries() {
        Map<String, SeoSitemapWriter.UrlEntry> entries = new LinkedHashMap<>();
        entries.put("/", new SeoSitemapWriter.UrlEntry("/", null));
        entries.put("/site", new SeoSitemapWriter.UrlEntry("/site", null));
        PUBLIC_SECTION_LABELS.keySet().stream()
                .filter(key -> !"/embed".equals(key) && !"/forms".equals(key))
                .sorted()
                .forEach(key -> entries.put(key, new SeoSitemapWriter.UrlEntry(key, null)));
        collectCmsEntries(entries);
        collectWikiEntries(entries);
        collectMarketEntries(entries);
        return new ArrayList<>(entries.values());
    }

    private void collectCmsEntries(Map<String, SeoSitemapWriter.UrlEntry> entries) {
        try {
            int page = 1;
            while (page <= 20) {
                CmsPageQuery query = new CmsPageQuery();
                query.setPage(page);
                query.setSize(50);
                PageResult<CmsPageDTO> result = cmsAppService.publicPages(query);
                if (result == null || result.getRecords() == null || result.getRecords().isEmpty()) {
                    return;
                }
                for (CmsPageDTO item : result.getRecords()) {
                    if (item.getSlug() == null || item.getSlug().isBlank()) {
                        continue;
                    }
                    entries.put("/site/" + item.getSlug(),
                            new SeoSitemapWriter.UrlEntry("/site/" + item.getSlug(), formatDate(item.getUpdateTime())));
                }
                if (result.getTotal() <= (long) page * result.getSize()) {
                    return;
                }
                page++;
            }
        }
        catch (Exception e) {
            log.debug("SEO sitemap 收集 CMS 页面失败，按空处理: {}", e.getMessage());
        }
    }

    private void collectWikiEntries(Map<String, SeoSitemapWriter.UrlEntry> entries) {
        try {
            for (WikiPublicSpaceDTO space : wikiSpaces()) {
                String spacePath = "/wiki/" + space.slug();
                entries.put(spacePath, new SeoSitemapWriter.UrlEntry(spacePath, null));
                for (WikiNodeDTO node : collectWikiPages(wikiTree(space.slug()))) {
                    String docPath = spacePath + "/" + node.getPath();
                    entries.put(docPath, new SeoSitemapWriter.UrlEntry(docPath, null));
                }
            }
        }
        catch (Exception e) {
            log.debug("SEO sitemap 收集知识库条目失败，按空处理: {}", e.getMessage());
        }
    }

    private void collectMarketEntries(Map<String, SeoSitemapWriter.UrlEntry> entries) {
        try {
            for (PluginMarketPublication item : marketPublished()) {
                if (item.getCode() == null || item.getCode().isBlank()) {
                    continue;
                }
                entries.put("/market/" + item.getCode(),
                        new SeoSitemapWriter.UrlEntry("/market/" + item.getCode(), null));
            }
        }
        catch (Exception e) {
            log.debug("SEO sitemap 收集插件市场条目失败，按空处理: {}", e.getMessage());
        }
    }

    private List<WikiNodeDTO> collectWikiPages(List<WikiNodeDTO> nodes) {
        List<WikiNodeDTO> pages = new ArrayList<>();
        for (WikiNodeDTO node : nodes) {
            if (node == null) {
                continue;
            }
            if (node.getNodeType() == WikiNodeType.PAGE) {
                pages.add(node);
            }
            if (node.getChildren() != null) {
                pages.addAll(collectWikiPages(node.getChildren()));
            }
        }
        return pages;
    }

    protected SeoSite loadSiteMeta() {
        try {
            Map<String, String> settings = settingAppService.publicSettings();
            return new SeoSite(
                    settings.getOrDefault("siteName", "").trim(),
                    settings.getOrDefault("siteDescription", "").trim(),
                    resolveAsset(settings.getOrDefault("logo", "").trim()));
        }
        catch (Exception e) {
            log.debug("SEO 读取站点设置失败，按空站点处理: {}", e.getMessage());
            return new SeoSite("", "", "");
        }
    }

    protected Optional<CmsPageDTO> findCmsPage(String slug) {
        try {
            return Optional.ofNullable(cmsAppService.publicPage(slug));
        }
        catch (Exception e) {
            return Optional.empty();
        }
    }

    protected List<WikiPublicSpaceDTO> wikiSpaces() {
        try {
            List<WikiPublicSpaceDTO> spaces = wikiPublicAppService.spaces();
            return spaces == null ? List.of() : spaces;
        }
        catch (Exception e) {
            return List.of();
        }
    }

    protected List<WikiNodeDTO> wikiTree(String spaceSlug) {
        try {
            List<WikiNodeDTO> tree = wikiPublicAppService.tree(spaceSlug);
            return tree == null ? List.of() : tree;
        }
        catch (Exception e) {
            return List.of();
        }
    }

    protected List<PluginMarketPublication> marketPublished() {
        try {
            List<PluginMarketPublication> publications = marketPublicationRepo.findByStatus(PluginPublicationStatus.PUBLISHED);
            return publications == null ? List.of() : publications;
        }
        catch (Exception e) {
            return List.of();
        }
    }

    private String findWikiDocTitle(String spaceSlug, String nodePath) {
        return collectWikiPages(wikiTree(spaceSlug)).stream()
                .filter(node -> nodePath.equals(node.getPath()))
                .map(WikiNodeDTO::getTitle)
                .filter(title -> title != null && !title.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String loadTemplate() {
        if (indexHtmlPath == null || indexHtmlPath.isBlank()) {
            return null;
        }
        try {
            Path file = Paths.get(indexHtmlPath);
            if (!Files.isRegularFile(file)) {
                log.warn("SEO 页面壳文件不存在: {}", indexHtmlPath);
                return null;
            }
            long modifiedAt = Files.getLastModifiedTime(file).toMillis();
            TemplateSnapshot snapshot = templateCache.get();
            if (snapshot == null || snapshot.modifiedAt() != modifiedAt) {
                snapshot = new TemplateSnapshot(modifiedAt, Files.readString(file, StandardCharsets.UTF_8));
                templateCache.set(snapshot);
            }
            return snapshot.html();
        }
        catch (IOException e) {
            log.warn("SEO 页面壳读取失败: {}", e.getMessage());
            return null;
        }
    }

    private String canonicalUrl(String path) {
        String base = siteUrl();
        return base.isBlank() ? null : base + path;
    }

    private String siteUrl() {
        return siteUrl == null ? "" : siteUrl.trim().replaceAll("/+$", "");
    }

    private String resolveAsset(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.startsWith("http://") || value.startsWith("https://") || !value.startsWith("/")) {
            return value;
        }
        String base = siteUrl();
        return base.isBlank() ? value : base + value;
    }

    private String joinTitle(String... parts) {
        StringBuilder title = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (title.length() > 0) {
                title.append(" - ");
            }
            title.append(part.trim());
        }
        return title.toString();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String formatDate(LocalDateTime time) {
        return time == null ? null : SITEMAP_DATE.format(LocalDate.from(time));
    }

    private record TemplateSnapshot(long modifiedAt, String html) {
    }

    /**
     * 站点级元数据（来自公开站点设置）。
     */
    protected record SeoSite(String name, String description, String logoUrl) {
    }
}
