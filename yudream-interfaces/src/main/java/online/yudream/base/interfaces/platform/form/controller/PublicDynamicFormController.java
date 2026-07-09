package online.yudream.base.interfaces.platform.form.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.form.service.DynamicFormAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.form.assembler.DynamicFormWebAssembler;
import online.yudream.base.interfaces.platform.form.request.FormSubmitRequest;
import online.yudream.base.interfaces.platform.form.res.DynamicFormRes;
import online.yudream.base.interfaces.platform.form.res.FormSubmissionRes;
import online.yudream.base.interfaces.system.file.assembler.FileWebAssembler;
import online.yudream.base.interfaces.system.file.res.FileObjectRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/public/forms")
@RequiredArgsConstructor
public class PublicDynamicFormController {

    private final DynamicFormAppService dynamicFormAppService;

    @GetMapping("/{code}")
    public Result<DynamicFormRes> form(@PathVariable String code) {
        return Result.ok(DynamicFormWebAssembler.toRes(dynamicFormAppService.publicForm(code)));
    }

    @PostMapping("/{code}/submissions")
    public Result<FormSubmissionRes> submit(@PathVariable String code, @RequestBody FormSubmitRequest request, HttpServletRequest httpRequest) {
        Long submitterId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return Result.ok(DynamicFormWebAssembler.toRes(dynamicFormAppService.submit(
                DynamicFormWebAssembler.toCmd(code, request, submitterId, clientIp(httpRequest))
        )));
    }

    @PostMapping("/{code}/files")
    public Result<FileObjectRes> upload(@PathVariable String code, @RequestParam("file") MultipartFile file) throws IOException {
        Long submitterId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return Result.ok(FileWebAssembler.toRes(dynamicFormAppService.uploadPublicFile(
                code,
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                submitterId
        )));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
