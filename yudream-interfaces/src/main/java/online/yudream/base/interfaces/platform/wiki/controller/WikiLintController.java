package online.yudream.base.interfaces.platform.wiki.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.interfaces.platform.ai.assembler.AiRunWebAssembler;
import online.yudream.base.interfaces.platform.ai.res.AiRunRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import online.yudream.base.application.platform.wiki.service.WikiLintAppService;
import online.yudream.base.domain.platform.wiki.anno.WikiSpaceAccess;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Lint：Wiki 健康检查。
 */
@RestController
@ConditionalOnProperty(name = {"yudream.platform.capabilities.ai.enabled", "yudream.platform.capabilities.wiki.enabled"}, havingValue = "true")
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiLintController {

    private final WikiLintAppService service;

    @PostMapping({"/spaces/{spaceId}/lint", "/spaces/{spaceId}/lint/runs"})
    @PermissionRegister(code = "platform:wiki:view", name = "Lint 检查 Wiki", module = "平台能力", desc = "检查矛盾、过时声明、孤立页面、缺失交叉引用与数据空白")
    @WikiSpaceAccess(action = WikiSpaceAclAction.READ, permission = "platform:wiki:view")
public Result<AiRunRes> lint(@PathVariable Long spaceId) {
        return Result.ok(AiRunWebAssembler.run(service.startLint(SecurityPrincipalSupport.current().userId(), spaceId)));
    }
}
