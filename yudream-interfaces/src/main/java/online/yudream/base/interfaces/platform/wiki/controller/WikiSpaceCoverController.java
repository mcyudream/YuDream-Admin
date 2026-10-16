package online.yudream.base.interfaces.platform.wiki.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.dto.WikiSpaceDTO;
import online.yudream.base.application.platform.wiki.service.WikiAccessPermissions;
import online.yudream.base.application.platform.wiki.service.WikiSpaceCoverAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiSpaceAccessWebAssembler;
import online.yudream.base.interfaces.platform.wiki.request.WikiSpaceCoverRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库封面管理：更新/随机/清除。上传封面只接受本人上传且公开可读的图片文件。
 */
@RestController
@RequestMapping("/api/platform/wiki/spaces/{spaceId}/cover")
@RequiredArgsConstructor
public class WikiSpaceCoverController {

    private final WikiSpaceCoverAppService service;

    @PutMapping
    @PermissionRegister(code = WikiAccessPermissions.COVER_EDIT, name = "更新 Wiki 封面", module = "平台能力",
            desc = "设置随机 seed 封面或引用公开图片文件封面")
    public Result<WikiSpaceDTO> update(@PathVariable Long spaceId, @RequestBody WikiSpaceCoverRequest request) {
        return Result.ok(service.update(WikiSpaceAccessWebAssembler.cover(spaceId, request)));
    }

    @PostMapping("/random")
    @PermissionRegister(code = WikiAccessPermissions.COVER_EDIT, name = "随机 Wiki 封面", module = "平台能力",
            desc = "由服务端生成稳定 seed 的随机封面")
    public Result<WikiSpaceDTO> random(@PathVariable Long spaceId,
                                       @RequestParam(required = false) String alt) {
        return Result.ok(service.random(spaceId, alt));
    }

    @DeleteMapping
    @PermissionRegister(code = WikiAccessPermissions.COVER_DELETE, name = "移除 Wiki 封面", module = "平台能力",
            desc = "清除封面引用（不删除共享文件）")
    public Result<WikiSpaceDTO> clear(@PathVariable Long spaceId) {
        return Result.ok(service.clear(spaceId));
    }
}
