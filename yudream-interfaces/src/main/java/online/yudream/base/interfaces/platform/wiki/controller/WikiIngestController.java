package online.yudream.base.interfaces.platform.wiki.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.service.WikiIngestAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiKnowledgeWebAssembler;
import online.yudream.base.interfaces.platform.wiki.res.WikiIngestTaskRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 摄入队列：入队、任务列表、取消、重试。
 */
@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiIngestController {

    private final WikiIngestAppService service;

    @PostMapping("/spaces/{spaceId}/sources/{sourceId}/ingest")
    @PermissionRegister(code = "platform:wiki:publish", name = "摄入 Wiki 资料", module = "平台能力", desc = "将原始资料入队并执行两步思维链摄入")
    public Result<Void> enqueue(@PathVariable Long spaceId, @PathVariable Long sourceId) {
        service.enqueueIngest(spaceId, sourceId);
        return Result.ok();
    }

    @GetMapping("/spaces/{spaceId}/ingest-tasks")
    @PermissionRegister(code = "platform:wiki:view", name = "查看摄入任务", module = "平台能力", desc = "查看知识库摄入队列与进度")
    public Result<List<WikiIngestTaskRes>> tasks(@PathVariable Long spaceId) {
        return Result.ok(WikiKnowledgeWebAssembler.toIngestTaskResList(service.tasks(spaceId)));
    }

    @PostMapping("/ingest-tasks/{id}/cancel")
    @PermissionRegister(code = "platform:wiki:publish", name = "取消摄入任务", module = "平台能力", desc = "取消排队中或运行中的摄入任务")
    public Result<Void> cancel(@PathVariable Long id) {
        service.cancelTask(id);
        return Result.ok();
    }

    @PostMapping("/ingest-tasks/{id}/retry")
    @PermissionRegister(code = "platform:wiki:publish", name = "重试摄入任务", module = "平台能力", desc = "重新入队失败或已取消的摄入任务")
    public Result<Void> retry(@PathVariable Long id) {
        service.retryTask(id);
        return Result.ok();
    }

    @DeleteMapping("/ingest-tasks/{id}")
    @PermissionRegister(code = "platform:wiki:publish", name = "取消摄入任务", module = "平台能力", desc = "删除摄入任务；运行中任务先取消")
    public Result<Void> delete(@PathVariable Long id) {
        service.deleteTask(id);
        return Result.ok();
    }

    @DeleteMapping("/spaces/{spaceId}/ingest-tasks")
    @PermissionRegister(code = "platform:wiki:publish", name = "取消摄入任务", module = "平台能力", desc = "清空知识库摄入队列")
    public Result<Integer> clear(@PathVariable Long spaceId) {
        return Result.ok(service.clearTasks(spaceId));
    }
}
