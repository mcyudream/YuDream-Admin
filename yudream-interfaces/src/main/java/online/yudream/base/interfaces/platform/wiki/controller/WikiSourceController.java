package online.yudream.base.interfaces.platform.wiki.controller;

import jakarta.validation.Valid;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope;
import online.yudream.base.domain.platform.wiki.anno.WikiSpaceAccess;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceResource;
import online.yudream.base.application.platform.wiki.service.WikiSourceAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiKnowledgeWebAssembler;
import online.yudream.base.interfaces.platform.wiki.request.WikiTextSourceRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiUrlImportRequest;
import online.yudream.base.interfaces.platform.wiki.res.WikiSourceRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 原始资料管理：上传、URL/批量导入、列表、删除、重新生成图片 caption。
 */
@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiSourceController {

    private final WikiSourceAppService service;

    @PostMapping("/spaces/{spaceId}/sources/upload")
    @PermissionRegister(code = "platform:wiki:manage", name = "导入 Wiki 资料", module = "平台能力", desc = "上传 PDF/Office 等原始资料并自动抽取文本与图片")
    @WikiSpaceAccess(action = WikiSpaceAclAction.EDIT, permission = "platform:wiki:manage")
    public Result<WikiSourceRes> upload(@PathVariable Long spaceId,
                                        @RequestParam(defaultValue = "/") String folderPath,
                                        @RequestPart("file") MultipartFile file) throws IOException {
        try (AiUsageExecutionScope ignored = AiUsageExecutionScope.open(SecurityPrincipalSupport.current().userId())) {
            return Result.ok(WikiKnowledgeWebAssembler.toRes(service.importFile(spaceId, folderPath, file.getInputStream(),
                    file.getOriginalFilename(), file.getContentType(), file.getSize())));
        }
    }

    @PostMapping("/spaces/{spaceId}/sources/import-urls")
    @PermissionRegister(code = "platform:wiki:manage", name = "批量导入 URL", module = "平台能力", desc = "抓取网页并作为原始资料摄入知识库")
    @WikiSpaceAccess(action = WikiSpaceAclAction.EDIT, permission = "platform:wiki:manage")
    public Result<List<WikiSourceRes>> importUrls(@PathVariable Long spaceId,
                                                  @Valid @RequestBody WikiUrlImportRequest request) {
        try (AiUsageExecutionScope ignored = AiUsageExecutionScope.open(SecurityPrincipalSupport.current().userId())) {
            return Result.ok(WikiKnowledgeWebAssembler.toSourceResList(
                    service.importUrls(spaceId, request.getFolderPath(), request.getUrls())));
        }
    }

    @PostMapping("/spaces/{spaceId}/sources/text")
    @PermissionRegister(code = "platform:wiki:manage", name = "新建在线文档资料", module = "平台能力", desc = "在线编写 Markdown 文本并作为原始资料摄入知识库")
    @WikiSpaceAccess(action = WikiSpaceAclAction.EDIT, permission = "platform:wiki:manage")
    public Result<WikiSourceRes> createText(@PathVariable Long spaceId,
                                            @Valid @RequestBody WikiTextSourceRequest request) {
        try (AiUsageExecutionScope ignored = AiUsageExecutionScope.open(SecurityPrincipalSupport.current().userId())) {
            return Result.ok(WikiKnowledgeWebAssembler.toRes(
                    service.createText(spaceId, request.getFolderPath(), request.getTitle(), request.getContent(),
                            StpUtil.getLoginIdAsLong())));
        }
    }

    @PutMapping("/sources/{id}/text")
    @PermissionRegister(code = "platform:wiki:edit", name = "编辑在线文档资料", module = "平台能力", desc = "编辑在线 Markdown 文本资料并触发重新摄入")
    @WikiSpaceAccess(action = WikiSpaceAclAction.EDIT, permission = "platform:wiki:edit", resource = WikiSpaceResource.SOURCE, idParam = "id")
    public Result<WikiSourceRes> updateText(@PathVariable Long id,
                                            @Valid @RequestBody WikiTextSourceRequest request) {
        try (AiUsageExecutionScope ignored = AiUsageExecutionScope.open(SecurityPrincipalSupport.current().userId())) {
            return Result.ok(WikiKnowledgeWebAssembler.toRes(
                    service.updateText(id, request.getTitle(), request.getContent(), StpUtil.getLoginIdAsLong())));
        }
    }

    @GetMapping("/spaces/{spaceId}/sources")
    @PermissionRegister(code = "platform:wiki:view", name = "查看 Wiki 资料", module = "平台能力", desc = "查看知识库原始资料列表")
    @WikiSpaceAccess(action = WikiSpaceAclAction.READ, permission = "platform:wiki:view")
    public Result<List<WikiSourceRes>> list(@PathVariable Long spaceId) {
        return Result.ok(WikiKnowledgeWebAssembler.toSourceResList(service.list(spaceId)));
    }

    @DeleteMapping("/sources/{id}")
    @PermissionRegister(code = "platform:wiki:delete", name = "删除 Wiki 资料", module = "平台能力", desc = "删除原始资料并级联清理其 Wiki 页面")
    @WikiSpaceAccess(action = WikiSpaceAclAction.EDIT, permission = "platform:wiki:delete", resource = WikiSpaceResource.SOURCE, idParam = "id")
    public Result<Void> delete(@PathVariable Long id) {
        try (AiUsageExecutionScope ignored = AiUsageExecutionScope.open(SecurityPrincipalSupport.current().userId())) {
            service.delete(id);
            return Result.ok();
        }
    }

}
