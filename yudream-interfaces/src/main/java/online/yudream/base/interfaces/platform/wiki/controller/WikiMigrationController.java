package online.yudream.base.interfaces.platform.wiki.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.service.WikiMigrationAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.request.WikiImportArchiveRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目迁移：归档导出/导入、重建索引。
 */
@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiMigrationController {

    private final WikiMigrationAppService service;

    @GetMapping("/spaces/{spaceId}/export")
    @PermissionRegister(code = "platform:wiki:manage", name = "导出 Wiki 归档", module = "平台能力", desc = "导出知识库页面与资料元数据为 JSON 归档")
    public Result<String> export(@PathVariable Long spaceId) {
        return Result.ok(service.exportArchive(spaceId));
    }

    @PostMapping("/import")
    @PermissionRegister(code = "platform:wiki:manage", name = "导入 Wiki 归档", module = "平台能力", desc = "从 JSON 归档导入知识库")
    public Result<String> importArchive(@Valid @RequestBody WikiImportArchiveRequest request) {
        return Result.ok(service.importArchive(request.getContent()));
    }

    @PostMapping("/spaces/{spaceId}/rebuild-index")
    @PermissionRegister(code = "platform:wiki:manage", name = "重建 Wiki 索引", module = "平台能力", desc = "从现有页面重建 index/overview 与向量索引")
    public Result<Void> rebuild(@PathVariable Long spaceId) {
        service.rebuildIndex(spaceId);
        return Result.ok();
    }
}
