package online.yudream.base.infra.platform.cms.mapper;

import online.yudream.base.domain.platform.cms.aggregate.CmsPage;
import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;
import online.yudream.base.infra.platform.cms.dataobj.CmsPageDO;
import online.yudream.base.infra.platform.cms.dataobj.HomePageLayoutDO;

public class CmsInfraMapper {

    private CmsInfraMapper() {
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
        dataObj.setMarkdownContent(domain.getMarkdownContent());
        dataObj.setSeoTitle(domain.getSeoTitle());
        dataObj.setSeoDescription(domain.getSeoDescription());
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
                .markdownContent(dataObj.getMarkdownContent())
                .seoTitle(dataObj.getSeoTitle())
                .seoDescription(dataObj.getSeoDescription())
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
