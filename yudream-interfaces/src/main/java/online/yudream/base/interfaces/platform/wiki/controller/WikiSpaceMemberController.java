package online.yudream.base.interfaces.platform.wiki.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.dto.WikiSpaceDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSpaceMemberDTO;
import online.yudream.base.application.platform.wiki.query.WikiSpaceMemberPageQuery;
import online.yudream.base.application.platform.wiki.service.WikiAccessPermissions;
import online.yudream.base.application.platform.wiki.service.WikiSpaceAclAppService;
import online.yudream.base.application.platform.wiki.service.WikiSpaceMemberAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiSpaceAccessWebAssembler;
import online.yudream.base.interfaces.platform.wiki.request.WikiSpaceMemberSaveRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiSpaceMemberUpdateRequest;
import online.yudream.base.interfaces.platform.wiki.res.WikiSpaceMemberRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库空间成员（实例 ACL）管理：全部端点需全局权限码 + 空间 MANAGE；
 * 受限空间不允许移除最后一名可管理成员。
 */
@RestController
@RequestMapping("/api/platform/wiki/spaces/{spaceId}")
@RequiredArgsConstructor
public class WikiSpaceMemberController {

    private final WikiSpaceMemberAppService service;
    private final WikiSpaceAclAppService accessService;

    @GetMapping("/members")
    @PermissionRegister(code = WikiAccessPermissions.MEMBER_VIEW, name = "查看 Wiki 空间成员", module = "平台能力",
            desc = "分页查看知识库空间成员授权")
    public Result<PageResult<WikiSpaceMemberRes>> page(@PathVariable Long spaceId, WikiSpaceMemberPageQuery query) {
        return Result.ok(WikiSpaceAccessWebAssembler.toMemberPage(
                service.page(WikiSpaceAccessWebAssembler.memberQuery(spaceId, query))));
    }

    @PostMapping("/members")
    @PermissionRegister(code = WikiAccessPermissions.MEMBER_MANAGE, name = "新增 Wiki 空间成员", module = "平台能力",
            desc = "为用户或角色授予空间动作")
    public Result<WikiSpaceMemberRes> create(@PathVariable Long spaceId,
                                             @Valid @RequestBody WikiSpaceMemberSaveRequest request) {
        WikiSpaceMemberDTO member = service.create(WikiSpaceAccessWebAssembler.member(spaceId, request));
        return Result.ok(WikiSpaceAccessWebAssembler.toRes(member));
    }

    @PutMapping("/members/{memberId}")
    @PermissionRegister(code = WikiAccessPermissions.MEMBER_MANAGE, name = "调整 Wiki 空间成员", module = "平台能力",
            desc = "按期望版本 CAS 调整空间成员动作")
    public Result<WikiSpaceMemberRes> update(@PathVariable Long spaceId, @PathVariable Long memberId,
                                             @RequestBody WikiSpaceMemberUpdateRequest request) {
        WikiSpaceMemberDTO member = service.update(WikiSpaceAccessWebAssembler.member(spaceId, memberId, request));
        return Result.ok(WikiSpaceAccessWebAssembler.toRes(member));
    }

    @DeleteMapping("/members/{memberId}")
    @PermissionRegister(code = WikiAccessPermissions.MEMBER_DELETE, name = "移除 Wiki 空间成员", module = "平台能力",
            desc = "移除空间成员授权（受限空间需保留可管理成员）")
    public Result<Void> delete(@PathVariable Long spaceId, @PathVariable Long memberId) {
        service.delete(spaceId, memberId);
        return Result.ok();
    }

    @PostMapping("/acl/restricted")
    @PermissionRegister(code = WikiAccessPermissions.MEMBER_MANAGE, name = "启用 Wiki 受限访问", module = "平台能力",
            desc = "显式启用空间实例 ACL，并确保存在可管理成员")
    public Result<WikiSpaceDTO> enableRestricted(@PathVariable Long spaceId) {
        return Result.ok(accessService.enableRestrictedAcl(spaceId));
    }
}
