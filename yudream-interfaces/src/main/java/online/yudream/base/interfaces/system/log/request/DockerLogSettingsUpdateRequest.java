package online.yudream.base.interfaces.system.log.request;

import java.util.List;

/**
 * 更新 Docker 容器日志采集配置请求。
 */
public record DockerLogSettingsUpdateRequest(boolean enabled, List<String> containers, String transport, String socket, long tail) {
}
