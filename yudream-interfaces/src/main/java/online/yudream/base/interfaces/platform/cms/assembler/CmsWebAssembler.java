package online.yudream.base.interfaces.platform.cms.assembler;

import online.yudream.base.application.platform.cms.cmd.CmsPageSaveCmd;
import online.yudream.base.application.platform.cms.cmd.HomePageLayoutSaveCmd;
import online.yudream.base.application.platform.cms.dto.CmsPageDTO;
import online.yudream.base.application.platform.cms.dto.HomePageLayoutDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.platform.cms.request.CmsPageSaveRequest;
import online.yudream.base.interfaces.platform.cms.request.HomePageLayoutSaveRequest;
import online.yudream.base.interfaces.platform.cms.res.CmsPageRes;
import online.yudream.base.interfaces.platform.cms.res.HomePageLayoutRes;

public class CmsWebAssembler {

    private CmsWebAssembler() {
    }

    public static CmsPageSaveCmd toCmd(CmsPageSaveRequest request) {
        CmsPageSaveCmd cmd = new CmsPageSaveCmd();
        cmd.setTitle(request.getTitle());
        cmd.setSlug(request.getSlug());
        cmd.setSummary(request.getSummary());
        cmd.setExcerpt(request.getExcerpt());
        cmd.setCoverImageUrl(request.getCoverImageUrl());
        cmd.setCategories(request.getCategories());
        cmd.setTags(request.getTags());
        cmd.setMarkdownContent(request.getMarkdownContent());
        cmd.setHtmlContent(request.getHtmlContent());
        cmd.setSeoTitle(request.getSeoTitle());
        cmd.setSeoDescription(request.getSeoDescription());
        cmd.setTemplate(request.getTemplate());
        cmd.setStatus(request.getStatus());
        return cmd;
    }

    public static CmsPageSaveCmd toCmd(Long id, CmsPageSaveRequest request) {
        CmsPageSaveCmd cmd = toCmd(request);
        cmd.setId(id);
        return cmd;
    }

    public static HomePageLayoutSaveCmd toCmd(HomePageLayoutSaveRequest request) {
        HomePageLayoutSaveCmd cmd = new HomePageLayoutSaveCmd();
        cmd.setTitle(request.getTitle());
        cmd.setSubtitle(request.getSubtitle());
        cmd.setTheme(request.getTheme());
        cmd.setHeroImageUrl(request.getHeroImageUrl());
        cmd.setSettings(request.getSettings());
        cmd.setSections(request.getSections());
        cmd.setPublished(request.getPublished());
        return cmd;
    }

    public static PageResult<CmsPageRes> toPage(PageResult<CmsPageDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(CmsWebAssembler::toRes).toList(),
                page.getTotal(), page.getPage(), page.getSize());
    }

    public static CmsPageRes toRes(CmsPageDTO dto) {
        return CmsPageRes.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .slug(dto.getSlug())
                .summary(dto.getSummary())
                .excerpt(dto.getExcerpt())
                .coverImageUrl(dto.getCoverImageUrl())
                .categories(dto.getCategories())
                .tags(dto.getTags())
                .markdownContent(dto.getMarkdownContent())
                .htmlContent(dto.getHtmlContent())
                .seoTitle(dto.getSeoTitle())
                .seoDescription(dto.getSeoDescription())
                .template(dto.getTemplate())
                .status(dto.getStatus())
                .publishedAt(dto.getPublishedAt())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static HomePageLayoutRes toRes(HomePageLayoutDTO dto) {
        return HomePageLayoutRes.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .subtitle(dto.getSubtitle())
                .theme(dto.getTheme())
                .heroImageUrl(dto.getHeroImageUrl())
                .settings(dto.getSettings())
                .sections(dto.getSections())
                .published(dto.getPublished())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }
}
