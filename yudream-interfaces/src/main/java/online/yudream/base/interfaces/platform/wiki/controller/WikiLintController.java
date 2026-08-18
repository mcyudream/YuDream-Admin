package online.yudream.base.interfaces.platform.wiki.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.service.WikiLintAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiKnowledgeWebAssembler;
import online.yudream.base.interfaces.platform.wiki.res.WikiLintReportRes;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lint：Wiki 健康检查。
 */
@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiLintController {

    private final WikiLintAppService service;

    @PostMapping("/spaces/{spaceId}/lint")
    @PermissionRegister(code = "platform:wiki:view", name = "Lint 检查 Wiki", module = "平台能力", desc = "检查矛盾、过时声明、孤立页面、缺失交叉引用与数据空白")
    public Result<WikiLintReportRes> lint(@PathVariable Long spaceId) {
        return Result.ok(WikiKnowledgeWebAssembler.toRes(service.lint(spaceId)));
    }
}
