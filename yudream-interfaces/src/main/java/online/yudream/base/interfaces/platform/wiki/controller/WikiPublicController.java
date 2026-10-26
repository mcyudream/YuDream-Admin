package online.yudream.base.interfaces.platform.wiki.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.dto.WikiNodeDTO;
import online.yudream.base.application.platform.wiki.dto.WikiPublicDocumentDTO;
import online.yudream.base.application.platform.wiki.dto.WikiPublicDocumentDetailDTO;
import online.yudream.base.application.platform.wiki.dto.WikiPublicSpaceDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.application.platform.wiki.service.WikiPublicAppService;
import online.yudream.base.application.platform.wiki.service.WikiIngestAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiWebAssembler;
import online.yudream.base.interfaces.platform.wiki.request.WikiChatRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiPublicSearchRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
        // 公开输出兜底剥离资料残留的 figure 标签（旧前端无渲染层剥离，避免显示为字面文本）
        List<WikiNodeDTO> nodes = service.tree(spaceSlug);
        stripFigureResidueDeep(nodes);
        return Result.ok(nodes);
    }

    private void stripFigureResidueDeep(List<WikiNodeDTO> nodes) {
        for (WikiNodeDTO node : nodes) {
            if (node.getBody() != null) {
                node.setBody(WikiIngestAppService.stripFigureResidue(node.getBody()));
            }
            if (node.getMarkdown() != null) {
                node.setMarkdown(WikiIngestAppService.stripFigureResidue(node.getMarkdown()));
            }
            stripFigureResidueDeep(node.getChildren());
        }
    }

    @GetMapping("/{spaceSlug}/documents")
    public Result<List<WikiPublicDocumentDTO>> documents(@PathVariable String spaceSlug) {
        return Result.ok(service.documents(spaceSlug));
    }

    @GetMapping("/{spaceSlug}/documents/{sourceId}")
    public Result<WikiPublicDocumentDetailDTO> document(@PathVariable String spaceSlug, @PathVariable Long sourceId) {
        WikiPublicDocumentDetailDTO detail = service.document(spaceSlug, sourceId);
        // 公开输出兜底剥离 figure 残留（record 不可变，重建实例）
        return Result.ok(new WikiPublicDocumentDetailDTO(detail.id(), detail.title(), detail.folderPath(),
                detail.kind(), detail.format(),
                detail.content() == null ? null : WikiIngestAppService.stripFigureResidue(detail.content()),
                detail.images()));
    }

    @PostMapping("/search")
    public Result<List<WikiSearchHitDTO>> search(@Valid @RequestBody WikiPublicSearchRequest request) {
        return Result.ok(service.searchAll(request.getQuery(), request.getSpaceSlug()));
    }

    @PostMapping("/{spaceSlug}/search")
    public Result<List<WikiSearchHitDTO>> search(@PathVariable String spaceSlug, @Valid @RequestBody WikiPublicSearchRequest request) {
        return Result.ok(service.search(spaceSlug, request.getQuery()));
    }

}
