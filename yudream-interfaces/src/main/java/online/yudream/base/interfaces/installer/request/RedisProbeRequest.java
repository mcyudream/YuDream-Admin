package online.yudream.base.interfaces.installer.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Redis 探测请求。
 */
@Data
public class RedisProbeRequest {

    @NotBlank(message = "Redis 地址不能为空")
    private String host;
    private Integer port;
    private String password;
    private Integer database;
    private Boolean ssl;
}
