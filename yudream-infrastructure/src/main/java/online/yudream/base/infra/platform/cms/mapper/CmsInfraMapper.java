package online.yudream.base.infra.platform.cms.mapper;

import online.yudream.base.domain.platform.cms.aggregate.CmsBlock;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;
import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;
import online.yudream.base.infra.platform.cms.dataobj.CmsBlockDO;
import online.yudream.base.infra.platform.cms.dataobj.CmsPageDO;
import online.yudream.base.infra.platform.cms.dataobj.HomePageLayoutDO;

public class CmsInfraMapper {

    private CmsInfraMapper() {
    }

    public static CmsBlockDO toDataObj(CmsBlock domain) {
        if (domain == null) {
            return null;
        }
        CmsBlockDO dataObj = new CmsBlockDO();
        dataObj.setId(domain.getId());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        dataObj.setCode(domain.getCode());
        dataObj.setName(domain.getName());
        dataObj.setDescription(domain.getDescription());
        dataObj.setCategory(domain.getCategory());
        dataObj.setKind(domain.getKind());
        dataObj.setIcon(domain.getIcon());
        dataObj.setPreviewImageUrl(domain.getPreviewImageUrl());
        dataObj.setHtmlContent(domain.getHtmlContent());
        dataObj.setCssContent(domain.getCssContent());
        dataObj.setJsContent(domain.getJsContent());
        dataObj.setBuilderProjectJson(domain.getBuilderProjectJson());
        dataObj.setTags(domain.getTags());
        dataObj.setEnabled(domain.getEnabled());
        dataObj.setBuiltin(domain.getBuiltin());
        dataObj.setSort(domain.getSort());
        return dataObj;
    }

    public static CmsBlock toDomain(CmsBlockDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return CmsBlock.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .code(dataObj.getCode())
                .name(dataObj.getName())
                .description(dataObj.getDescription())
                .category(dataObj.getCategory())
                .kind(dataObj.getKind())
                .icon(dataObj.getIcon())
                .previewImageUrl(dataObj.getPreviewImageUrl())
                .htmlContent(dataObj.getHtmlContent())
                .cssContent(dataObj.getCssContent())
                .jsContent(dataObj.getJsContent())
                .builderProjectJson(dataObj.getBuilderProjectJson())
                .tags(dataObj.getTags())
                .enabled(dataObj.getEnabled())
                .builtin(dataObj.getBuiltin())
                .sort(dataObj.getSort())
                .build();
    }

    public static CmsPageDO toDataObj(CmsPage domain) {
        if (domain == null) {
            return null;
        }
        CmsPageDO dataObj = new CmsPageDO();
        dataObj.setId(domain.getId());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        dataObj.setTitle(domain.getTitle());
        dataObj.setSlug(domain.getSlug());
        dataObj.setSummary(domain.getSummary());
        dataObj.setExcerpt(domain.getExcerpt());
        dataObj.setCoverImageUrl(domain.getCoverImageUrl());
        dataObj.setCategories(domain.getCategories());
        dataObj.setTags(domain.getTags());
        dataObj.setMarkdownContent(domain.getMarkdownContent());
        dataObj.setHtmlContent(domain.getHtmlContent());
        dataObj.setCssContent(domain.getCssContent());
        dataObj.setJsContent(domain.getJsContent());
        dataObj.setBuilderProjectJson(domain.getBuilderProjectJson());
        dataObj.setSeoTitle(domain.getSeoTitle());
        dataObj.setSeoDescription(domain.getSeoDescription());
        dataObj.setTemplate(domain.getTemplate());
        dataObj.setStatus(domain.getStatus());
        dataObj.setPublishedAt(domain.getPublishedAt());
        return dataObj;
    }

    public static CmsPage toDomain(CmsPageDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return CmsPage.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .title(dataObj.getTitle())
                .slug(dataObj.getSlug())
                .summary(dataObj.getSummary())
                .excerpt(dataObj.getExcerpt())
                .coverImageUrl(dataObj.getCoverImageUrl())
                .categories(dataObj.getCategories())
                .tags(dataObj.getTags())
                .markdownContent(dataObj.getMarkdownContent())
                .htmlContent(dataObj.getHtmlContent())
                .cssContent(dataObj.getCssContent())
                .jsContent(dataObj.getJsContent())
                .builderProjectJson(dataObj.getBuilderProjectJson())
                .seoTitle(dataObj.getSeoTitle())
                .seoDescription(dataObj.getSeoDescription())
                .template(dataObj.getTemplate())
                .status(dataObj.getStatus())
                .publishedAt(dataObj.getPublishedAt())
                .build();
    }

    public static HomePageLayoutDO toDataObj(HomePageLayout domain) {
        if (domain == null) {
            return null;
        }
        HomePageLayoutDO dataObj = new HomePageLayoutDO();
        dataObj.setId(domain.getId());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        dataObj.setTitle(domain.getTitle());
        dataObj.setSubtitle(domain.getSubtitle());
        dataObj.setTheme(domain.getTheme());
        dataObj.setHeroImageUrl(domain.getHeroImageUrl());
        dataObj.setSettings(domain.getSettings());
        dataObj.setSections(domain.getSections());
        dataObj.setPublished(domain.getPublished());
        return dataObj;
    }

    public static HomePageLayout toDomain(HomePageLayoutDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return HomePageLayout.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .title(dataObj.getTitle())
                .subtitle(dataObj.getSubtitle())
                .theme(dataObj.getTheme())
                .heroImageUrl(dataObj.getHeroImageUrl())
                .settings(dataObj.getSettings())
                .sections(dataObj.getSections())
                .published(dataObj.getPublished())
                .build();
    }
}
