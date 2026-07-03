package online.yudream.base.application.platform.cms.assembler;

import online.yudream.base.application.platform.cms.dto.CmsPageDTO;
import online.yudream.base.application.platform.cms.dto.HomePageLayoutDTO;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;
import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;

public class CmsAssembler {

    private CmsAssembler() {
    }

    public static CmsPageDTO toDTO(CmsPage page) {
        return CmsPageDTO.builder()
                .id(page.getId())
                .title(page.getTitle())
                .slug(page.getSlug())
                .summary(page.getSummary())
                .markdownContent(page.getMarkdownContent())
                .seoTitle(page.getSeoTitle())
                .seoDescription(page.getSeoDescription())
                .status(page.getStatus())
                .publishedAt(page.getPublishedAt())
                .createTime(page.getCreateTime())
                .updateTime(page.getUpdateTime())
                .build();
    }

    public static HomePageLayoutDTO toDTO(HomePageLayout layout) {
        return HomePageLayoutDTO.builder()
                .id(layout.getId())
                .title(layout.getTitle())
                .subtitle(layout.getSubtitle())
                .theme(layout.getTheme())
                .heroImageUrl(layout.getHeroImageUrl())
                .settings(layout.getSettings())
                .sections(layout.getSections())
                .published(layout.getPublished())
                .createTime(layout.getCreateTime())
                .updateTime(layout.getUpdateTime())
                .build();
    }
}
