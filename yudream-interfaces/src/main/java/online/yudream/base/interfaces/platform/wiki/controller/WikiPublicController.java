package online.yudream.base.interfaces.platform.wiki.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.dto.WikiNodeDTO;
import online.yudream.base.application.platform.wiki.dto.WikiPublicSpaceDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.application.platform.wiki.service.WikiPublicAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.request.WikiSearchRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/wiki")
@RequiredArgsConstructor
public class WikiPublicController {
    private final WikiPublicAppService service;

    @GetMapping("/spaces")
    public Result<List<WikiPublicSpaceDTO>> spaces() {
        return Result.ok(service.spaces());
    }

    @GetMapping("/{spaceSlug}/tree")
    public Result<List<WikiNodeDTO>> tree(@PathVariable String spaceSlug) {
        return Result.ok(service.tree(spaceSlug));
    }

    @PostMapping("/{spaceSlug}/search")
    public Result<List<WikiSearchHitDTO>> search(@PathVariable String spaceSlug, @Valid @RequestBody WikiSearchRequest request) {
        return Result.ok(service.search(spaceSlug, request.getQuery(), request.getTopK(), request.getPathPrefix(), request.isGraphExpansion()));
    }
}
