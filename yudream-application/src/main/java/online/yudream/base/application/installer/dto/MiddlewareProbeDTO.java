package online.yudream.base.application.installer.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 中间件探测结果 DTO（Mongo 与 Redis 通用）。
 */
@Data
@Builder
public class MiddlewareProbeDTO {

    /** 探测目标：Mongo 为连接串（不含密码时），Redis 为 host:port。 */
    private String target;
    /** MONGO / REDIS。 */
    private String kind;
    private boolean reachable;
    private boolean authRequired;
    private boolean authFailed;
    private String version;
    private long latencyMs;
    private String message;
}
