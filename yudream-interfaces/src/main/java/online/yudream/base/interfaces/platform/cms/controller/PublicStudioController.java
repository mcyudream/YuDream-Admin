package online.yudream.base.interfaces.platform.cms.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.cms.service.StudioAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.cms.assembler.StudioWebAssembler;
import online.yudream.base.interfaces.platform.cms.res.StudioPublicDocumentRes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.cms", name = "enabled", havingValue = "true")
public class PublicStudioController {
    private final StudioAppService studioAppService;
    @GetMapping("/api/public/cms/studio")
    public Result<StudioPublicDocumentRes> document() {
        return Result.ok(StudioWebAssembler.toRes(studioAppService.publicDocument()));
    }
}
