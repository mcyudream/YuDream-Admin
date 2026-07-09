package online.yudream.base.plugin.projectprogress.application.dto;

import java.util.List;

public record ProjectUserOptionDTO(
        String id,
        String username,
        String nickname,
        String email,
        String avatar,
        String status,
        List<String> deptIds,
        List<String> deptNames
) {
}
