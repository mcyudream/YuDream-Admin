package online.yudream.base.interfaces.platform.cms.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.cms.query.CmsPageQuery;
import online.yudream.base.application.platform.cms.service.CmsAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.cms.assembler.CmsWebAssembler;
import online.yudream.base.interfaces.platform.cms.res.CmsPageRes;
import online.yudream.base.interfaces.platform.cms.res.HomePageLayoutRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/cms")
@RequiredArgsConstructor
public class PublicCmsController {

    private final CmsAppService cmsAppService;

    @GetMapping("/home")
    public Result<HomePageLayoutRes> home() {
        return Result.ok(CmsWebAssembler.toRes(cmsAppService.publicHome()));
    }

    @GetMapping("/pages")
    public Result<CmsPageRes> page(@RequestParam String slug) {
        return Result.ok(CmsWebAssembler.toRes(cmsAppService.publicPage(slug)));
    }

    @GetMapping("/pages/list")
    public Result<PageResult<CmsPageRes>> pages(CmsPageQuery query) {
        return Result.ok(CmsWebAssembler.toPage(cmsAppService.publicPages(query)));
    }
}
