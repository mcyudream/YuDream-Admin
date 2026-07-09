package online.yudream.base.plugin.projectprogress.application.dto;

import java.util.List;

public record ProjectDeptOptionDTO(
        String id,
        String name,
        String parentId,
        String status,
        List<ProjectDeptOptionDTO> children
) {
}
