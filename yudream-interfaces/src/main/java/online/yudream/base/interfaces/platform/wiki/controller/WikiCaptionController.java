package online.yudream.base.interfaces.platform.wiki.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.service.WikiSourceAppService;
import online.yudream.base.domain.platform.wiki.anno.WikiSpaceAccess;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceResource;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.ai.assembler.AiRunWebAssembler;
import online.yudream.base.interfaces.platform.ai.res.AiRunRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"yudream.platform.capabilities.ai.enabled", "yudream.platform.capabilities.wiki.enabled"}, havingValue = "true")
public class WikiCaptionController {
    private final WikiSourceAppService service;

    @PostMapping({"/sources/{id}/caption-images", "/sources/{id}/caption-images/runs"})
    @PermissionRegister(code = "platform:wiki:edit", name = "重新生成图片描述", module = "平台能力", desc = "提交后台图片描述任务并实时查看运行进度")
    @WikiSpaceAccess(action = WikiSpaceAclAction.EDIT, permission = "platform:wiki:edit", resource = WikiSpaceResource.SOURCE, idParam = "id")
public Result<AiRunRes> captionImages(@PathVariable Long id) {
        return Result.ok(AiRunWebAssembler.run(service.startCaptionImages(id, SecurityPrincipalSupport.current().userId())));
    }
}
