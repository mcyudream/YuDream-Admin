package online.yudream.base.application.system.backup.dto;

import java.time.LocalDateTime;

/** 异地备份目标视图（密码不外泄，仅返回是否已设置）。 */
public record RemoteTargetDTO(
        String id,
        String code,
        String name,
        String type,
        String host,
        Integer port,
        String username,
        String basePath,
        boolean passiveMode,
        boolean insecureTls,
        boolean enabled,
        boolean passwordSet,
        LocalDateTime createTime
) {
}
