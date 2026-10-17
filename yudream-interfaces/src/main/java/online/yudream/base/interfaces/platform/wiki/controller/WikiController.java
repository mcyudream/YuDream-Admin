package online.yudream.base.interfaces.platform.wiki.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.dto.WikiNodeDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSpaceDTO;
import online.yudream.base.application.platform.wiki.service.WikiSpaceAclAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiWebAssembler;
import online.yudream.base.interfaces.platform.wiki.request.WikiNodeMoveRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiNodeSaveRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiSpaceSaveRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台侧知识库/目录用例入口。
 * <p>
 * 全部写用例经 {@link WikiSpaceAclAppService}：能力闸门 → 全局权限码 → 空间实例 ACL；
 * 存量空间（LEGACY）保持既有全局权限行为，受限空间（RESTRICTED）按成员授权 fail closed。
 */
@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiController {

    private final WikiSpaceAclAppService access;

    @GetMapping("/spaces")
    @PermissionRegister(code = "platform:wiki:view", name = "查看 Wiki 知识库", module = "平台能力", desc = "查看知识库列表")
    public Result<List<WikiSpaceDTO>> spaces() {
        return Result.ok(access.spaces());
    }

    @GetMapping("/spaces/{id}")
    @PermissionRegister(code = "platform:wiki:view", name = "查看 Wiki 知识库详情", module = "平台能力", desc = "查看单个知识库详情（含卡片聚合事实）")
    public Result<WikiSpaceDTO> spaceDetail(@PathVariable Long id) {
        return Result.ok(access.spaceDetail(id));
    }

    @PostMapping("/spaces")
    @PermissionRegister(code = "platform:wiki:manage", name = "创建 Wiki 知识库", module = "平台能力", desc = "创建知识库")
    public Result<WikiSpaceDTO> create(@Valid @RequestBody WikiSpaceSaveRequest r) {
        return Result.ok(access.saveSpace(WikiWebAssembler.space(null, r)));
    }

    @PutMapping("/spaces/{id}")
    @PermissionRegister(code = "platform:wiki:manage", name = "编辑 Wiki 知识库", module = "平台能力", desc = "编辑知识库")
    public Result<WikiSpaceDTO> update(@PathVariable Long id, @Valid @RequestBody WikiSpaceSaveRequest r) {
        return Result.ok(access.saveSpace(WikiWebAssembler.space(id, r)));
    }

    @DeleteMapping("/spaces/{id}")
    @PermissionRegister(code = "platform:wiki:delete", name = "删除 Wiki 知识库", module = "平台能力", desc = "级联删除知识库及其全部内容")
    public Result<Void> deleteSpace(@PathVariable Long id) {
        access.deleteSpace(id);
        return Result.ok();
    }

    @GetMapping("/spaces/{spaceId}/tree")
    @PermissionRegister(code = "platform:wiki:view", name = "查看 Wiki 目录", module = "平台能力", desc = "查看知识库目录")
    public Result<List<WikiNodeDTO>> tree(@PathVariable Long spaceId) {
        return Result.ok(access.tree(spaceId));
    }

    @PostMapping("/spaces/{spaceId}/nodes")
    @PermissionRegister(code = "platform:wiki:edit", name = "创建 Wiki 节点", module = "平台能力", desc = "创建目录或页面")
    public Result<WikiNodeDTO> createNode(@PathVariable Long spaceId, @Valid @RequestBody WikiNodeSaveRequest r) {
        return Result.ok(access.saveNode(WikiWebAssembler.node(null, spaceId, r)));
    }

    @PutMapping("/spaces/{spaceId}/nodes/{id}")
    @PermissionRegister(code = "platform:wiki:edit", name = "编辑 Wiki 节点", module = "平台能力", desc = "编辑目录或页面")
    public Result<WikiNodeDTO> updateNode(@PathVariable Long spaceId, @PathVariable Long id,
                                          @Valid @RequestBody WikiNodeSaveRequest r) {
        return Result.ok(access.saveNode(WikiWebAssembler.node(id, spaceId, r)));
    }

    @PostMapping("/nodes/{id}/move")
    @PermissionRegister(code = "platform:wiki:edit", name = "移动 Wiki 节点", module = "平台能力", desc = "移动知识库目录")
    public Result<Void> move(@PathVariable Long id, @RequestBody WikiNodeMoveRequest r) {
        access.moveNode(id, r.getParentId() == null || r.getParentId().isBlank() ? null : Long.valueOf(r.getParentId()));
        return Result.ok();
    }

    @DeleteMapping("/nodes/{id}")
    @PermissionRegister(code = "platform:wiki:delete", name = "删除 Wiki 节点", module = "平台能力", desc = "删除空目录或页面")
    public Result<Void> delete(@PathVariable Long id) {
        access.deleteNode(id);
        return Result.ok();
    }

    @PostMapping("/nodes/{id}/publish")
    @PermissionRegister(code = "platform:wiki:publish", name = "发布 Wiki 页面", module = "平台能力", desc = "发布并建立检索索引")
    public Result<Void> publish(@PathVariable Long id) {
        access.publish(id);
        return Result.ok();
    }

    @PostMapping("/nodes/{id}/unpublish")
    @PermissionRegister(code = "platform:wiki:publish", name = "取消发布 Wiki 页面", module = "平台能力", desc = "撤销发布和检索索引")
    public Result<Void> unpublish(@PathVariable Long id) {
        access.unpublish(id);
        return Result.ok();
    }
}
