package online.yudream.base.interfaces.platform.theme.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.theme.query.ThemePublicContextQuery;
import online.yudream.base.application.platform.theme.service.ThemePublicContextAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.theme.assembler.ThemePublicContextWebAssembler;
import online.yudream.base.interfaces.platform.theme.res.ThemePublicContextRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开主题上下文端点：匿名可访问，供 Vue 原生主题页按需聚合
 * 主题配置、插件数据块与 CMS 最新文章。
 */
@RestController
@RequestMapping("/api/public/theme")
@RequiredArgsConstructor
public class PublicThemeController {

    private final ThemePublicContextAppService themePublicContextAppService;

    @GetMapping("/context")
    public Result<ThemePublicContextRes> context(ThemePublicContextQuery query) {
        return Result.ok(ThemePublicContextWebAssembler.toRes(themePublicContextAppService.context(query)));
    }
}
