package online.yudream.base.application.system.backup.cmd;

import online.yudream.base.domain.system.backup.enumerate.RemoteTargetType;

/** 创建/更新异地备份目标命令。password 为空表示保持原密码。 */
public record RemoteTargetCmd(
        String code,
        String name,
        RemoteTargetType type,
        String host,
        Integer port,
        String username,
        String password,
        String basePath,
        boolean passiveMode,
        Boolean insecureTls
) {
}
