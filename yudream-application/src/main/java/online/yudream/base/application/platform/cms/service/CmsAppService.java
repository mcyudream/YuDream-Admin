package online.yudream.base.application.platform.cms.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.cms.assembler.CmsAssembler;
import online.yudream.base.application.platform.cms.cmd.CmsPageSaveCmd;
import online.yudream.base.application.platform.cms.cmd.HomePageLayoutSaveCmd;
import online.yudream.base.application.platform.cms.dto.CmsPageDTO;
import online.yudream.base.application.platform.cms.dto.HomePageLayoutDTO;
import online.yudream.base.application.platform.cms.query.CmsPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;
import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.repo.CmsPageRepo;
import online.yudream.base.domain.platform.cms.repo.HomePageLayoutRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CmsAppService {

    private static final String CAPABILITY_CODE = "cms";

    private final CapabilityModuleRepo capabilityModuleRepo;
    private final CmsPageRepo cmsPageRepo;
    private final HomePageLayoutRepo homePageLayoutRepo;

    @Transactional(readOnly = true)
    public PageResult<CmsPageDTO> page(CmsPageQuery query) {
        PageResult<CmsPage> page = cmsPageRepo.page(query.getKeyword(), query.getPage(), query.getSize());
        return new PageResult<>(page.getRecords().stream().map(CmsAssembler::toDTO).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    @Transactional
    public CmsPageDTO savePage(CmsPageSaveCmd cmd) {
        ensureEnabled();
        CmsPage page = cmd.getId() == null ? createPage(cmd) : page(cmd.getId());
        page.update(cmd.getTitle(), cmd.getSummary(), cmd.getMarkdownContent(), cmd.getSeoTitle(), cmd.getSeoDescription(), cmd.getStatus());
        return CmsAssembler.toDTO(cmsPageRepo.save(page));
    }

    @Transactional
    public void publish(Long id) {
        ensureEnabled();
        CmsPage page = page(id);
        page.publish();
        cmsPageRepo.save(page);
    }

    @Transactional
    public void unpublish(Long id) {
        ensureEnabled();
        CmsPage page = page(id);
        page.unpublish();
        cmsPageRepo.save(page);
    }

    @Transactional(readOnly = true)
    public HomePageLayoutDTO homeLayout() {
        return CmsAssembler.toDTO(homePageLayoutRepo.findCurrent().orElseGet(HomePageLayout::defaultLayout));
    }

    @Transactional
    public HomePageLayoutDTO saveHomeLayout(HomePageLayoutSaveCmd cmd) {
        ensureEnabled();
        HomePageLayout layout = homePageLayoutRepo.findCurrent().orElseGet(HomePageLayout::defaultLayout);
        layout.update(cmd.getTitle(), cmd.getSubtitle(), cmd.getTheme(), cmd.getHeroImageUrl(), cmd.getSettings(), cmd.getSections(), cmd.getPublished());
        return CmsAssembler.toDTO(homePageLayoutRepo.save(layout));
    }

    @Transactional(readOnly = true)
    public HomePageLayoutDTO publicHome() {
        ensureEnabled();
        HomePageLayout layout = homePageLayoutRepo.findCurrent().orElseThrow(() -> new BizException("首页未配置"));
        if (!Boolean.TRUE.equals(layout.getPublished())) {
            throw new BizException("首页未发布");
        }
        return CmsAssembler.toDTO(layout);
    }

    @Transactional(readOnly = true)
    public CmsPageDTO publicPage(String slug) {
        ensureEnabled();
        CmsPage page = cmsPageRepo.findBySlug(slug).orElseThrow(() -> new BizException("页面不存在"));
        if (page.getStatus() != PageStatus.PUBLISHED) {
            throw new BizException("页面未发布");
        }
        return CmsAssembler.toDTO(page);
    }

    private CmsPage createPage(CmsPageSaveCmd cmd) {
        if (cmsPageRepo.findBySlug(cmd.getSlug()).isPresent()) {
            throw new BizException("页面路径已存在");
        }
        return CmsPage.create(cmd.getTitle(), cmd.getSlug());
    }

    private CmsPage page(Long id) {
        return cmsPageRepo.findById(id).orElseThrow(() -> new BizException("页面不存在"));
    }

    private void ensureEnabled() {
        boolean enabled = capabilityModuleRepo.findByCode(CAPABILITY_CODE)
                .map(module -> Boolean.TRUE.equals(module.getEnabled()))
                .orElse(false);
        if (!enabled) {
            throw new BizException("内容定制能力未启用");
        }
    }
}
