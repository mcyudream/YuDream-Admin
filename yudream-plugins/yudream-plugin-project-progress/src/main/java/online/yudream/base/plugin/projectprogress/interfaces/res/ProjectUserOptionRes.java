package online.yudream.base.plugin.projectprogress.interfaces.res;

import java.util.List;

public record ProjectUserOptionRes(
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
