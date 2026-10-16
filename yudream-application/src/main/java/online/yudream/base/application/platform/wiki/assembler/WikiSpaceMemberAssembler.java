package online.yudream.base.application.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.dto.WikiSpaceMemberDTO;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpaceMember;

import java.util.List;
import java.util.Map;

/**
 * 空间成员领域到应用 DTO 的转换。subjectName 只用于展示，权限判定依据类型与 ID。
 */
public final class WikiSpaceMemberAssembler {

    private WikiSpaceMemberAssembler() {
    }

    public static WikiSpaceMemberDTO toDTO(WikiSpaceMember member, String subjectName) {
        if (member == null) {
            return null;
        }
        return WikiSpaceMemberDTO.builder()
                .id(id(member.getId()))
                .spaceId(id(member.getSpaceId()))
                .subjectType(member.getSubjectType() == null ? null : member.getSubjectType().name())
                .subjectId(id(member.getSubjectId()))
                .subjectName(subjectName == null ? "" : subjectName)
                .actions(member.getActions() == null ? List.of()
                        : member.getActions().stream().map(Enum::name).toList())
                .createdByUserId(id(member.getCreatedByUserId()))
                .version(member.getVersion())
                .build();
    }

    public static List<WikiSpaceMemberDTO> toDTOs(List<WikiSpaceMember> members, Map<String, String> subjectNames) {
        if (members == null) {
            return List.of();
        }
        return members.stream()
                .map(member -> toDTO(member, subjectNames.get(subjectKey(member))))
                .toList();
    }

    private static String subjectKey(WikiSpaceMember member) {
        if (member == null || member.getSubjectType() == null || member.getSubjectId() == null) {
            return "";
        }
        return member.getSubjectType().name() + ":" + member.getSubjectId();
    }

    private static String id(Long value) {
        return value == null ? null : value.toString();
    }
}
