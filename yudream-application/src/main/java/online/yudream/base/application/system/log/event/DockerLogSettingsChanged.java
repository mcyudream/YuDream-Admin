package online.yudream.base.application.system.log.event;

import online.yudream.base.application.system.log.dto.DockerLogSettingsDTO;

/**
 * Docker 容器日志采集配置已变更事件，由应用服务发布、基础设施采集源订阅。
 */
public record DockerLogSettingsChanged(DockerLogSettingsDTO settings) {
}
