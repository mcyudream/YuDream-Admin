package online.yudream.base.interfaces.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.cmd.WikiSpaceCoverCmd;
import online.yudream.base.application.platform.wiki.cmd.WikiSpaceMemberSaveCmd;
import online.yudream.base.application.platform.wiki.cmd.WikiSpaceMemberUpdateCmd;
import online.yudream.base.application.platform.wiki.dto.WikiPublicSpaceProfileDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSpaceMemberDTO;
import online.yudream.base.application.platform.wiki.query.WikiCommentPageQuery;
import online.yudream.base.application.platform.wiki.query.WikiSpaceMemberPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceCoverType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;
import online.yudream.base.interfaces.platform.wiki.request.WikiSpaceCoverRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiSpaceMemberSaveRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiSpaceMemberUpdateRequest;
import online.yudream.base.interfaces.platform.wiki.res.WikiPublicSpaceProfileRes;
import online.yudream.base.interfaces.platform.wiki.res.WikiSpaceMemberRes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 空间封面/成员/公开资料的接口层转换：request -> cmd、DTO -> res、分页参数归一。
 */
public final class WikiSpaceAccessWebAssembler {

    private static final int MAX_PAGE_SIZE = 100;

    private WikiSpaceAccessWebAssembler() {
    }

    public static WikiSpaceCoverCmd cover(Long spaceId, WikiSpaceCoverRequest request) {
        WikiSpaceCoverCmd cmd = new WikiSpaceCoverCmd();
        cmd.setSpaceId(spaceId);
        cmd.setCoverType(parseCoverType(request.getCoverType()));
        cmd.setCoverFileObjectId(parseId(request.getFileObjectId(), "封面文件"));
        cmd.setCoverSeed(request.getSeed());
        cmd.setCoverAlt(request.getAlt());
        return cmd;
    }

    public static WikiSpaceMemberSaveCmd member(Long spaceId, WikiSpaceMemberSaveRequest request) {
        WikiSpaceMemberSaveCmd cmd = new WikiSpaceMemberSaveCmd();
        cmd.setSpaceId(spaceId);
        cmd.setSubjectType(parseSubjectType(request.getSubjectType()));
        cmd.setSubjectId(parseId(request.getSubjectId(), "成员主体"));
        cmd.setActions(parseActions(request.getActions()));
        return cmd;
    }

    public static WikiSpaceMemberUpdateCmd member(Long spaceId, Long memberId, WikiSpaceMemberUpdateRequest request) {
        WikiSpaceMemberUpdateCmd cmd = new WikiSpaceMemberUpdateCmd();
        cmd.setSpaceId(spaceId);
        cmd.setMemberId(memberId);
        cmd.setActions(parseActions(request.getActions()));
        cmd.setExpectedVersion(request.getExpectedVersion());
        return cmd;
    }

    public static WikiSpaceMemberPageQuery memberQuery(Long spaceId, WikiSpaceMemberPageQuery query) {
        if (query == null) {
            query = new WikiSpaceMemberPageQuery();
        }
        query.setSpaceId(spaceId);
        normalize(query);
        return query;
    }

    public static WikiCommentPageQuery commentQuery(Long spaceId, WikiCommentPageQuery query) {
        if (query == null) {
            query = new WikiCommentPageQuery();
        }
        query.setSpaceId(spaceId);
        normalize(query);
        return query;
    }

    public static PageResult<WikiSpaceMemberRes> toMemberPage(PageResult<WikiSpaceMemberDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(WikiSpaceAccessWebAssembler::toRes).toList(),
                page.getTotal(), page.getPage(), page.getSize());
    }

    public static WikiSpaceMemberRes toRes(WikiSpaceMemberDTO dto) {
        if (dto == null) {
            return null;
        }
        return WikiSpaceMemberRes.builder()
                .id(dto.getId())
                .spaceId(dto.getSpaceId())
                .subjectType(dto.getSubjectType())
                .subjectId(dto.getSubjectId())
                .subjectName(dto.getSubjectName())
                .actions(dto.getActions())
                .createdByUserId(dto.getCreatedByUserId())
                .version(dto.getVersion())
                .build();
    }

    public static WikiPublicSpaceProfileRes toRes(WikiPublicSpaceProfileDTO dto) {
        if (dto == null) {
            return null;
        }
        return WikiPublicSpaceProfileRes.builder()
                .name(dto.name())
                .slug(dto.slug())
                .description(dto.description())
                .coverType(dto.coverType())
                .coverSeed(dto.coverSeed())
                .coverUrl(dto.coverUrl())
                .coverAlt(dto.coverAlt())
                .commentEnabled(dto.commentEnabled())
                .build();
    }

    public static List<WikiPublicSpaceProfileRes> toResList(List<WikiPublicSpaceProfileDTO> profiles) {
        if (profiles == null) {
            return List.of();
        }
        return profiles.stream().map(WikiSpaceAccessWebAssembler::toRes).toList();
    }

    /** 列表分页参数归一，避免一次拉全量。 */
    private static void normalize(online.yudream.base.application.common.PageQuery query) {
        query.setPage(Math.max(query.getPage(), 1));
        query.setSize(Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE));
    }

    private static WikiSpaceCoverType parseCoverType(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException("封面类型不能为空");
        }
        try {
            return WikiSpaceCoverType.valueOf(value.trim().toUpperCase());
        }
        catch (IllegalArgumentException error) {
            throw new BizException("封面类型不合法：" + value);
        }
    }

    private static WikiSpaceMemberSubjectType parseSubjectType(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException("成员主体类型不能为空");
        }
        try {
            return WikiSpaceMemberSubjectType.valueOf(value.trim().toUpperCase());
        }
        catch (IllegalArgumentException error) {
            throw new BizException("成员主体类型不合法：" + value);
        }
    }

    private static List<WikiSpaceAclAction> parseActions(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new BizException("成员动作不能为空");
        }
        Set<WikiSpaceAclAction> actions = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                actions.add(WikiSpaceAclAction.valueOf(value.trim().toUpperCase()));
            }
            catch (IllegalArgumentException error) {
                throw new BizException("成员动作不合法：" + value);
            }
        }
        if (actions.isEmpty()) {
            throw new BizException("成员动作不能为空");
        }
        return new ArrayList<>(actions);
    }

    private static Long parseId(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        }
        catch (NumberFormatException error) {
            throw new BizException(label + " ID 不合法");
        }
    }
}
