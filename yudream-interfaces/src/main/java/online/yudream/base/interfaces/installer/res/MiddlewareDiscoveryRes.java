package online.yudream.base.interfaces.installer.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 中间件自动发现结果响应。
 */
@Data
@Builder
public class MiddlewareDiscoveryRes {

    private List<MiddlewareProbeRes> mongo;
    private List<MiddlewareProbeRes> redis;
}
