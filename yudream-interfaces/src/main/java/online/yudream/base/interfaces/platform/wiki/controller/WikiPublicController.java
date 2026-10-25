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
import online.yudream.base.application.platform.wiki.service.WikiPublicImageAppService;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiWebAssembler;
import online.yudream.base.interfaces.platform.wiki.request.WikiChatRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiPublicSearchRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/public/wiki")
@RequiredArgsConstructor
public class WikiPublicController {
    private final WikiPublicAppService service;
    private final WikiPublicImageAppService imageService;

    @GetMapping("/spaces")
    public Result<List<WikiPublicSpaceDTO>> spaces() {
        return Result.ok(service.spaces());
    }

    @GetMapping("/{spaceSlug}/tree")
    public Result<List<WikiNodeDTO>> tree(@PathVariable String spaceSlug) {
        return Result.ok(service.tree(spaceSlug));
    }

    /**
     * 已发布页面引用图片的公开代理：服务端按「空间已公开 + 节点已发布 + 发布正文引用 + 空间 Wiki 图片资产」
     * 判定（见 {@link WikiPublicImageAppService#content}）。发布撤回/空间关闭公开阅读后同路径立即拒绝，
     * 因此响应禁用长缓存，每次使用前重新校验。
     */
    @GetMapping("/{spaceSlug}/nodes/{nodeId}/images/{fileId}")
    public ResponseEntity<InputStreamResource> nodeImage(@PathVariable String spaceSlug,
                                                         @PathVariable Long nodeId,
                                                         @PathVariable Long fileId) {
        return imageResponse(imageService.content(spaceSlug, nodeId, fileId));
    }

    private ResponseEntity<InputStreamResource> imageResponse(FileContentDTO content) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.getContentType() == null ? "image/png" : content.getContentType());
        }
        catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String fileName = content.getOriginalName() == null || content.getOriginalName().isBlank()
                ? "image"
                : content.getOriginalName();
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(new InputStreamResource(content.getInputStream()));
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

}
