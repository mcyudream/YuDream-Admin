package online.yudream.base.infra.platform.workspace.runner;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 代码运行器（yudream-code-runner 容器）连接配置。enabled 是部署闸门：关闭时不创建运行器端口，
 * 依赖它的 AI 工具不出现；工作流 Python 仍走本机解释器。
 */
@Data
@Component
@ConfigurationProperties(prefix = "yudream.platform.code-runner")
public class CodeRunnerProperties {
    private boolean enabled = false;
    private String baseUrl = "http://code-runner:8090";
    private String token = "";
    /** 单次运行的 HTTP 等待上限（应大于运行器的最长运行时间） */
    private Duration timeout = Duration.ofSeconds(150);
}
