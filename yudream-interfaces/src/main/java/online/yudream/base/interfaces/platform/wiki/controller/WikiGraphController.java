package online.yudream.base.interfaces.platform.wiki.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.service.WikiGraphAnalysisAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiKnowledgeWebAssembler;
import online.yudream.base.interfaces.platform.wiki.res.WikiGraphSnapshotRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识图谱：四信号关联度、Louvain 社区检测与图谱洞察。
 */
@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiGraphController {

    private final WikiGraphAnalysisAppService service;

    @GetMapping("/spaces/{spaceId}/graph")
    @PermissionRegister(code = "platform:wiki:view", name = "查看 Wiki 知识图谱", module = "平台能力", desc = "查看 wikilink/来源重叠/类型亲和的关联图、社区与洞察")
    public Result<WikiGraphSnapshotRes> graph(@PathVariable Long spaceId) {
        return Result.ok(WikiKnowledgeWebAssembler.toRes(service.snapshot(spaceId)));
    }
}
