package online.yudream.base.interfaces.platform.wiki.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.dto.WikiNodeDTO;
import online.yudream.base.application.platform.wiki.dto.WikiPublicDocumentDTO;
import online.yudream.base.application.platform.wiki.dto.WikiPublicDocumentDetailDTO;
import online.yudream.base.application.platform.wiki.dto.WikiPublicSpaceDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.application.platform.wiki.service.WikiChatAppService;
import online.yudream.base.application.platform.wiki.service.WikiPublicAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiWebAssembler;
import online.yudream.base.interfaces.platform.wiki.request.WikiChatRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiPublicSearchRequest;
import online.yudream.base.interfaces.platform.wiki.support.PublicWikiChatExecution;
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
    private final WikiChatAppService chatService;
    private final PublicWikiChatExecution execution;

    @GetMapping("/spaces")
    public Result<List<WikiPublicSpaceDTO>> spaces() {
        return Result.ok(service.spaces());
    }

    @GetMapping("/{spaceSlug}/tree")
    public Result<List<WikiNodeDTO>> tree(@PathVariable String spaceSlug) {
        return Result.ok(service.tree(spaceSlug));
    }

    @GetMapping("/{spaceSlug}/documents")
    public Result<List<WikiPublicDocumentDTO>> documents(@PathVariable String spaceSlug) {
        return Result.ok(service.documents(spaceSlug));
    }

    @GetMapping("/{spaceSlug}/documents/{sourceId}")
    public Result<WikiPublicDocumentDetailDTO> document(@PathVariable String spaceSlug, @PathVariable Long sourceId) {
        return Result.ok(service.document(spaceSlug, sourceId));
    }

    @PostMapping("/search")
    public Result<List<WikiSearchHitDTO>> search(@Valid @RequestBody WikiPublicSearchRequest request) {
        return Result.ok(service.searchAll(request.getQuery(), request.getSpaceSlug()));
    }

    @PostMapping("/{spaceSlug}/search")
    public Result<List<WikiSearchHitDTO>> search(@PathVariable String spaceSlug, @Valid @RequestBody WikiPublicSearchRequest request) {
        return Result.ok(service.search(spaceSlug, request.getQuery()));
    }

    @PostMapping(value = "/{spaceSlug}/chat/agui", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable String spaceSlug,
                           @Valid @RequestBody WikiChatRequest request,
                           HttpServletRequest httpRequest) {
        // 只信任容器解析出的远端地址，不直接采信可被客户端伪造的 X-Forwarded-For。
        // 先验证公开知识库，避免任意伪造 slug 生成无限限流键。
        service.ensurePublicSpace(spaceSlug);
        return execution.start(
                httpRequest.getRemoteAddr(),
                spaceSlug,
                (onDelta, onReasoning, onTool, onActivity) -> chatService.chatStreamBySlug(
                        spaceSlug,
                        request.getQuestion(),
                        WikiWebAssembler.chatHistory(request),
                        onDelta,
                        onReasoning,
                        onTool,
                        onActivity))
                .emitter();
    }
}
