package online.yudream.base.application.system.log.cmd;

import java.util.List;

/**
 * 更新 Docker 容器日志采集配置命令。
 */
public record DockerLogSettingsUpdateCmd(boolean enabled, List<String> containers, String transport, String socket, long tail) {
}
