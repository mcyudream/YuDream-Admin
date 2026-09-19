package online.yudream.base.interfaces.system.file.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.application.system.file.query.FileObjectPageQuery;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.file.assembler.FileWebAssembler;
import online.yudream.base.interfaces.system.file.res.FileObjectRes;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    /** 管理权限：持有个权限的角色可跨用户查看/管理全部文件。 */
    public static final String FILE_MANAGE_PERMISSION = "system:file:manage";

    /** 允许内联展示的类型；其余一律按附件下载，防止 HTML/SVG 内联执行。 */
    private static final Set<String> INLINE_SAFE_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp", "image/avif", "image/x-icon",
            "video/mp4", "video/webm", "video/ogg",
            "audio/mpeg", "audio/ogg", "audio/wav", "audio/webm",
            "application/pdf",
            "text/plain"
    );

    private final FileAppService fileAppService;

    @GetMapping
    public Result<PageResult<FileObjectRes>> page(FileObjectPageQuery query) {
        StpUtil.checkLogin();
        // 非管理者仅能列出本人上传与公开文件，防止枚举他人文件元数据。
        return Result.ok(FileWebAssembler.toPage(fileAppService.page(
                query, StpUtil.getLoginIdAsLong(), StpUtil.hasPermission(FILE_MANAGE_PERMISSION))));
    }

    /** 全量文件管理视图（跨用户），需显式管理权限。 */
    @GetMapping("/manage/page")
    @PermissionRegister(code = FILE_MANAGE_PERMISSION, name = "管理全部文件", module = "系统管理", desc = "跨用户查询与管理全部文件")
    public Result<PageResult<FileObjectRes>> managePage(FileObjectPageQuery query) {
        StpUtil.checkPermission(FILE_MANAGE_PERMISSION);
        return Result.ok(FileWebAssembler.toPage(fileAppService.page(query, null, true)));
    }

    @PostMapping("/upload")
    public Result<FileObjectRes> upload(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "module", required = false) String module,
                                        @RequestParam(value = "publicAccess", defaultValue = "false") boolean publicAccess) throws IOException {
        StpUtil.checkLogin();
        return Result.ok(FileWebAssembler.toRes(fileAppService.upload(
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                module,
                StpUtil.getLoginIdAsLong(),
                publicAccess)));
    }

    @GetMapping("/{id}")
    public Result<FileObjectRes> get(@PathVariable Long id) {
        StpUtil.checkLogin();
        return Result.ok(FileWebAssembler.toRes(fileAppService.get(
                id, StpUtil.getLoginIdAsLong(), StpUtil.hasPermission(FILE_MANAGE_PERMISSION))));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable Long id) {
        FileContentDTO content;
        if (StpUtil.isLogin()) {
            content = fileAppService.content(id, StpUtil.getLoginIdAsLong(),
                    StpUtil.hasPermission(FILE_MANAGE_PERMISSION));
        } else {
            // 匿名仅允许读取显式公开的文件，其余一律要求登录。
            content = fileAppService.publicContent(id);
        }
        return contentResponse(id, content);
    }

    @GetMapping("/{id}/content/thumb")
    public ResponseEntity<InputStreamResource> thumb(@PathVariable Long id) {
        FileContentDTO content;
        if (StpUtil.isLogin()) {
            content = fileAppService.thumbnailContent(id, StpUtil.getLoginIdAsLong(),
                    StpUtil.hasPermission(FILE_MANAGE_PERMISSION));
        } else {
            content = fileAppService.publicThumbnailContent(id);
        }
        return contentResponse(id, content);
    }

    @GetMapping("/public/{id}/content")
    public ResponseEntity<InputStreamResource> publicContent(@PathVariable Long id) {
        return contentResponse(id, fileAppService.publicContent(id));
    }

    @GetMapping("/public/{id}/content/thumb")
    public ResponseEntity<InputStreamResource> publicThumb(@PathVariable Long id) {
        return contentResponse(id, fileAppService.publicThumbnailContent(id));
    }

    private ResponseEntity<InputStreamResource> contentResponse(Long id, FileContentDTO content) {
        String storedType = content.getContentType();
        boolean inlineSafe = StringUtils.hasText(storedType)
                && INLINE_SAFE_CONTENT_TYPES.contains(storedType.split(";")[0].trim().toLowerCase());
        MediaType mediaType;
        String disposition = "inline";
        if (inlineSafe) {
            mediaType = MediaType.parseMediaType(storedType);
        } else {
            // 未识别/可执行内容一律按二进制附件下发，不回显攻击者可控的 Content-Type。
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
            disposition = "attachment";
        }
        String fileName = StringUtils.hasText(content.getOriginalName()) ? content.getOriginalName() : id.toString();
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        // 文件 ID 对应内容不可变，长缓存避免列表封面反复打满浏览器连接。
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.getContentLength() == null ? -1 : content.getContentLength())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=2592000, immutable")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename*=UTF-8''" + encodedName)
                .body(new InputStreamResource(content.getInputStream()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        StpUtil.checkLogin();
        fileAppService.delete(id, StpUtil.getLoginIdAsLong(), StpUtil.hasPermission(FILE_MANAGE_PERMISSION));
        return Result.ok();
    }
}
