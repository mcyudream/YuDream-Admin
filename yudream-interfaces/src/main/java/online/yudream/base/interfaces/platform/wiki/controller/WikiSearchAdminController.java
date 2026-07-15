package online.yudream.base.interfaces.platform.wiki.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.application.platform.wiki.service.WikiSearchAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.request.WikiSearchRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiSearchAdminController {
    private final WikiSearchAppService search;

    @PostMapping("/search-test")
    @PermissionRegister(code = "platform:wiki:view", name = "测试 Wiki 检索", module = "平台能力", desc = "使用知识库当前向量索引执行管理端检索测试")
    public Result<List<WikiSearchHitDTO>> search(@Valid @RequestBody WikiSearchRequest request) {
        return Result.ok(search.searchForAdmin(request.getSpaceSlug(), request.getQuery(), request.getTopK(), request.getPathPrefix(), request.isGraphExpansion()));
    }
}
