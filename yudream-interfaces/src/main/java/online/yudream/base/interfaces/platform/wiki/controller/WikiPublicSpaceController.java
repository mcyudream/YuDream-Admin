package online.yudream.base.interfaces.platform.wiki.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.service.WikiSpaceCoverAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiSpaceAccessWebAssembler;
import online.yudream.base.interfaces.platform.wiki.res.WikiPublicSpaceProfileRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开端知识库资料：门户封面列表与单库资料（含评论开关）。
 * 只返回公开空间，且只暴露安全封面字段（类型/seed/公开 URL/替代文本）。
 */
@RestController
@RequestMapping("/api/public/wiki")
@RequiredArgsConstructor
public class WikiPublicSpaceController {

    private final WikiSpaceCoverAppService service;

    @GetMapping("/portal")
    public Result<List<WikiPublicSpaceProfileRes>> portal() {
        return Result.ok(WikiSpaceAccessWebAssembler.toResList(service.profiles()));
    }

    @GetMapping("/{spaceSlug}/profile")
    public Result<WikiPublicSpaceProfileRes> profile(@PathVariable String spaceSlug) {
        return Result.ok(WikiSpaceAccessWebAssembler.toRes(service.profile(spaceSlug)));
    }
}
