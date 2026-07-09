package online.yudream.base.plugin.projectprogress.interfaces.res;

import java.util.List;

public record ProjectDeptOptionRes(
        String id,
        String name,
        String parentId,
        String status,
        List<ProjectDeptOptionRes> children
) {
}
