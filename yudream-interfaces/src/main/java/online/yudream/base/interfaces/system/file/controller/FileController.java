package online.yudream.base.interfaces.system.file.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.application.system.file.query.FileObjectPageQuery;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.file.assembler.FileWebAssembler;
import online.yudream.base.interfaces.system.file.res.FileObjectRes;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
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
import java.time.Duration;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileAppService fileAppService;

    @GetMapping
    public Result<PageResult<FileObjectRes>> page(FileObjectPageQuery query) {
        StpUtil.checkLogin();
        return Result.ok(FileWebAssembler.toPage(fileAppService.page(query)));
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
        return Result.ok(FileWebAssembler.toRes(fileAppService.get(id)));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable Long id) {
        FileContentDTO content = fileAppService.content(id);
        MediaType mediaType = StringUtils.hasText(content.getContentType())
                ? MediaType.parseMediaType(content.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        String fileName = StringUtils.hasText(content.getOriginalName()) ? content.getOriginalName() : id.toString();
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.getContentLength() == null ? -1 : content.getContentLength())
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .body(new InputStreamResource(content.getInputStream()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        StpUtil.checkLogin();
        fileAppService.delete(id);
        return Result.ok();
    }
}
