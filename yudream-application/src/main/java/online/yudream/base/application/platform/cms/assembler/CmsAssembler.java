package online.yudream.base.application.platform.cms.assembler;

import online.yudream.base.application.platform.cms.dto.CmsBlockDTO;
import online.yudream.base.application.platform.cms.dto.CmsPageDTO;
import online.yudream.base.application.platform.cms.dto.HomePageLayoutDTO;
import online.yudream.base.domain.platform.cms.aggregate.CmsBlock;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;
import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;

public class CmsAssembler {

    private CmsAssembler() {
    }

    public static CmsBlockDTO toDTO(CmsBlock block) {
        return CmsBlockDTO.builder()
                .id(block.getId())
                .code(block.getCode())
                .name(block.getName())
                .description(block.getDescription())
                .category(block.getCategory())
                .kind(block.getKind())
                .icon(block.getIcon())
                .previewImageUrl(block.getPreviewImageUrl())
                .htmlContent(block.getHtmlContent())
                .cssContent(block.getCssContent())
                .jsContent(block.getJsContent())
                .builderProjectJson(block.getBuilderProjectJson())
                .tags(block.getTags())
                .enabled(block.getEnabled())
                .builtin(block.getBuiltin())
                .sort(block.getSort())
                .createTime(block.getCreateTime())
                .updateTime(block.getUpdateTime())
                .build();
    }

    public static CmsBlock toDomain(CmsBlockDTO dto) {
        if (dto == null) {
            return null;
        }
        CmsBlock block = new CmsBlock();
        block.setId(dto.getId());
        block.setCode(dto.getCode());
        block.setName(dto.getName());
        block.setDescription(dto.getDescription());
        block.setCategory(dto.getCategory());
        block.setKind(dto.getKind());
        block.setIcon(dto.getIcon());
        block.setPreviewImageUrl(dto.getPreviewImageUrl());
        block.setHtmlContent(dto.getHtmlContent());
        block.setCssContent(dto.getCssContent());
        block.setJsContent(dto.getJsContent());
        block.setBuilderProjectJson(dto.getBuilderProjectJson());
        block.setTags(dto.getTags());
        block.setEnabled(dto.getEnabled());
        block.setBuiltin(dto.getBuiltin());
        block.setSort(dto.getSort());
        block.setCreateTime(dto.getCreateTime());
        block.setUpdateTime(dto.getUpdateTime());
        return block;
    }

    public static CmsPageDTO toDTO(CmsPage page) {
        return CmsPageDTO.builder()
                .id(page.getId())
                .title(page.getTitle())
                .slug(page.getSlug())
                .summary(page.getSummary())
                .excerpt(page.getExcerpt())
                .coverImageUrl(page.getCoverImageUrl())
                .categories(page.getCategories())
                .tags(page.getTags())
                .markdownContent(page.getMarkdownContent())
                .htmlContent(page.getHtmlContent())
                .cssContent(page.getCssContent())
                .jsContent(page.getJsContent())
                .builderProjectJson(page.getBuilderProjectJson())
                .seoTitle(page.getSeoTitle())
                .seoDescription(page.getSeoDescription())
                .template(page.getTemplate())
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
