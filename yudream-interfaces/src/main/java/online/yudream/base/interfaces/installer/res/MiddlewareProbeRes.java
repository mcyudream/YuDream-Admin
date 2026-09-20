package online.yudream.base.interfaces.installer.res;

import lombok.Builder;
import lombok.Data;

/**
 * 中间件探测结果响应。
 */
@Data
@Builder
public class MiddlewareProbeRes {

    private String target;
    private String kind;
    private boolean reachable;
    private boolean authRequired;
    private boolean authFailed;
    private String version;
    private long latencyMs;
    private String message;
}
