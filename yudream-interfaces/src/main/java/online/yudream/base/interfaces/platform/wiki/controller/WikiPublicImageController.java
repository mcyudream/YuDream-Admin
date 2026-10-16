package online.yudream.base.interfaces.platform.wiki.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.service.WikiPublicImageAppService;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开 Wiki 图片代理：{@code /api/public/wiki/{spaceSlug}/nodes/{nodeId}/images/{fileId}}。
 * <p>
 * 无登录注解、匿名可达；安全边界在应用层：空间已公开 + 节点已发布 + 文件被该发布版本正文引用，
 * 且只输出 image/* 内容。未发布的资料/草稿图片与未被发布正文引用的文件一律拒绝，
 * 因此该端点不构成任意文件读取代理，也不依赖文件级 publicAccess 标记。
 * 该路径属于 WikiAccessCoverage 已登记的“公开阅读轴”例外（空间显式公开），不接入平台实例 ACL。
 */
@RestController
@RequestMapping("/api/public/wiki")
@RequiredArgsConstructor
public class WikiPublicImageController {

    private final WikiPublicImageAppService service;

    @GetMapping("/{spaceSlug}/nodes/{nodeId}/images/{fileId}")
    public ResponseEntity<InputStreamResource> image(@PathVariable String spaceSlug,
                                                     @PathVariable Long nodeId,
                                                     @PathVariable Long fileId) {
        FileContentDTO content = service.content(spaceSlug, nodeId, fileId);
        String storedType = content.getContentType() == null ? "" : content.getContentType().split(";")[0].trim();
        // 应用层已确保 image/*；此处只做防御性回填，绝不回显非图片 Content-Type
        MediaType mediaType = storedType.toLowerCase().startsWith("image/")
                ? MediaType.parseMediaType(storedType)
                : MediaType.APPLICATION_OCTET_STREAM;
        // 发布可被撤回（取消发布/关闭公开阅读）：使用短缓存，避免长缓存绕过撤回
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.getContentLength() == null ? -1 : content.getContentLength())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=600")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(content.getInputStream()));
    }
}
