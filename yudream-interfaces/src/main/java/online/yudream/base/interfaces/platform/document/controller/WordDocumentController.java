package online.yudream.base.interfaces.platform.document.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.document.query.WordDocumentPageQuery;
import online.yudream.base.application.platform.document.service.WordDocumentAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.document.assembler.WordDocumentWebAssembler;
import online.yudream.base.interfaces.platform.document.request.WordGenerateRequest;
import online.yudream.base.interfaces.platform.document.request.WordTemplateSaveRequest;
import online.yudream.base.interfaces.platform.document.res.WordGenerationRecordRes;
import online.yudream.base.interfaces.platform.document.res.WordTemplateRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/platform/documents")
@RequiredArgsConstructor
public class WordDocumentController {

    private final WordDocumentAppService wordDocumentAppService;

    @GetMapping("/word-templates")
    @PermissionRegister(code = "platform:document:view", name = "查看Word模板", module = "平台能力", desc = "查看 Word 模板列表")
    public Result<PageResult<WordTemplateRes>> templates(WordDocumentPageQuery query) {
        return Result.ok(WordDocumentWebAssembler.toTemplatePage(wordDocumentAppService.pageTemplates(query)));
    }

    @PostMapping("/word-templates")
    @PermissionRegister(code = "platform:document:edit", name = "上传Word模板", module = "平台能力", desc = "上传 Word 模板文件并配置占位符")
    public Result<WordTemplateRes> uploadTemplate(@RequestPart("file") MultipartFile file,
                                                  @Valid @RequestPart("meta") WordTemplateSaveRequest request) throws IOException {
        return Result.ok(WordDocumentWebAssembler.toRes(wordDocumentAppService.uploadTemplate(
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                WordDocumentWebAssembler.toCmd(request),
                StpUtil.getLoginIdAsLong()
        )));
    }

    @PutMapping("/word-templates/{id}")
    @PermissionRegister(code = "platform:document:edit", name = "编辑Word模板", module = "平台能力", desc = "编辑 Word 模板配置")
    public Result<WordTemplateRes> updateTemplate(@PathVariable Long id, @Valid @RequestBody WordTemplateSaveRequest request) {
        return Result.ok(WordDocumentWebAssembler.toRes(
                wordDocumentAppService.updateTemplate(WordDocumentWebAssembler.toCmd(id, request))));
    }

    @PutMapping("/word-templates/{id}/file")
    @PermissionRegister(code = "platform:document:edit", name = "替换Word模板文件", module = "平台能力", desc = "替换 Word 模板原文件")
    public Result<WordTemplateRes> replaceTemplateFile(@PathVariable Long id, @RequestPart("file") MultipartFile file) throws IOException {
        return Result.ok(WordDocumentWebAssembler.toRes(wordDocumentAppService.replaceTemplateFile(
                id,
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                StpUtil.getLoginIdAsLong()
        )));
    }

    @DeleteMapping("/word-templates/{id}")
    @PermissionRegister(code = "platform:document:edit", name = "停用Word模板", module = "平台能力", desc = "停用 Word 模板")
    public Result<Void> disableTemplate(@PathVariable Long id) {
        wordDocumentAppService.disableTemplate(id);
        return Result.ok();
    }

    @PostMapping("/word-templates/{id}/generate")
    @PermissionRegister(code = "platform:document:generate", name = "生成Word文档", module = "平台能力", desc = "根据 Word 模板生成报告或证明文件")
    public Result<WordGenerationRecordRes> generate(@PathVariable Long id, @RequestBody WordGenerateRequest request) {
        return Result.ok(WordDocumentWebAssembler.toRes(wordDocumentAppService.generate(
                WordDocumentWebAssembler.toCmd(id, request, StpUtil.getLoginIdAsLong()))));
    }

    @GetMapping("/word-records")
    @PermissionRegister(code = "platform:document:log:view", name = "查看Word生成记录", module = "平台能力", desc = "查看 Word 模板生成记录")
    public Result<PageResult<WordGenerationRecordRes>> records(WordDocumentPageQuery query) {
        return Result.ok(WordDocumentWebAssembler.toRecordPage(wordDocumentAppService.pageRecords(query)));
    }
}
