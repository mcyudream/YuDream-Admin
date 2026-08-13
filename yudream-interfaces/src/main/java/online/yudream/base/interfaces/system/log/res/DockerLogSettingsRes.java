package online.yudream.base.interfaces.system.log.res;

import java.util.List;

/**
 * Docker 容器日志采集配置返回对象。
 */
public record DockerLogSettingsRes(boolean enabled, List<String> containers, String transport, String socket, long tail) {
}
