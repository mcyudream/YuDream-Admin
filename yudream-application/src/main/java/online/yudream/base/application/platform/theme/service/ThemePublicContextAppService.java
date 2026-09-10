package online.yudream.base.application.platform.theme.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.cms.dto.CmsTemplateItemDTO;
import online.yudream.base.application.platform.theme.dto.ThemePublicContextDTO;
import online.yudream.base.application.platform.theme.query.ThemePublicContextQuery;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;
import online.yudream.base.domain.platform.cms.repo.CmsPageRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 公开主题上下文：为 Vue 原生主题页聚合主题配置、插件数据块与 CMS 最新文章。
 * 匿名可访问：主题配置经 publicConfig 脱敏，块数据由提供者协议约束只输出公开字段，
 * CMS 文章仅输出列表轻量字段；CMS 能力关闭时文章列表降级为空。
 */
@Service
@RequiredArgsConstructor
public class ThemePublicContextAppService {

    private static final int DEFAULT_LATEST_LIMIT = 8;
    private static final int MAX_LATEST_LIMIT = 50;

    private final SiteThemeQueryService siteThemeQueryService;
    private final ThemeConfigAppService themeConfigAppService;
    private final ThemeBlockAppService themeBlockAppService;
    private final CapabilityAppService capabilities;
    private final CmsPageRepo cmsPages;

    @Transactional(readOnly = true)
    public ThemePublicContextDTO context(ThemePublicContextQuery query) {
        ThemePublicContextQuery safeQuery = query == null ? new ThemePublicContextQuery() : query;
        String theme = siteThemeQueryService.activeSiteThemeCode();
        return ThemePublicContextDTO.builder()
                .themeCode(theme)
                .themeConfig(themeConfigAppService.publicConfig(theme))
                .blocks(themeBlockAppService.resolveBlocks(parseBlocks(safeQuery.getBlocks()), theme, safeQuery.getLimit()))
                .cmsPagesLatest(latestCmsPages(theme, safeQuery.getCmsLatest()))
                .build();
    }

    private List<String> parseBlocks(String blocks) {
        if (!StringUtils.hasText(blocks)) {
            return List.of();
        }
        return Arrays.stream(blocks.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<CmsTemplateItemDTO> latestCmsPages(String theme, Integer requested) {
        int limit = bounded(requested);
        if (limit == 0 || !capabilities.enabled("cms")) {
            return List.of();
        }
        return cmsPages.publishedPage(theme, null, null, null, 1, limit)
                .getRecords().stream()
                .sorted(Comparator.comparing(CmsPage::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(this::latestItem)
                .toList();
    }

    private CmsTemplateItemDTO latestItem(CmsPage page) {
        return CmsTemplateItemDTO.builder()
                .id(String.valueOf(page.getId()))
                .source("cms")
                .title(page.getTitle())
                .slug(page.getSlug())
                .summary(page.getSummary())
                .excerpt(page.getExcerpt())
                .url("/site/" + page.getSlug())
                .publishedAt(format(page.getPublishedAt()))
                .updatedAt(format(page.getUpdateTime()))
                .build();
    }

    private int bounded(Integer requested) {
        if (requested == null) {
            return DEFAULT_LATEST_LIMIT;
        }
        if (requested <= 0) {
            return 0;
        }
        return Math.min(requested, MAX_LATEST_LIMIT);
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
