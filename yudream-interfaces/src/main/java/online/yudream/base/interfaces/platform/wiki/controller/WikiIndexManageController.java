package online.yudream.base.interfaces.platform.wiki.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.dto.WikiIndexSnapshotDTO;
import online.yudream.base.application.platform.wiki.service.WikiIndexManageAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiIndexManageController {
    private final WikiIndexManageAppService indexes;

    @GetMapping("/nodes/{nodeId}/index-results")
    @PermissionRegister(code = "platform:wiki:view", name = "查看 Wiki 索引结果", module = "平台能力", desc = "查看已发布页面的向量分块与知识图谱关系")
    public Result<WikiIndexSnapshotDTO> inspect(@PathVariable Long nodeId) {
        return Result.ok(indexes.inspect(nodeId));
    }
}
