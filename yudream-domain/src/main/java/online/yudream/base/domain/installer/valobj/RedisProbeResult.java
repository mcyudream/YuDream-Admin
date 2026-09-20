package online.yudream.base.domain.installer.valobj;

/**
 * Redis 探测结果：reachable 表示网络可达；authRequired 表示服务器要求认证但未提供凭据；
 * authFailed 表示凭据被拒绝（WRONGPASS）。
 */
public record RedisProbeResult(
        boolean reachable,
        boolean authRequired,
        boolean authFailed,
        String version,
        long latencyMs,
        String message
) {

    public static RedisProbeResult ok(long latencyMs, String version) {
        return new RedisProbeResult(true, false, false, version, latencyMs, "连接成功");
    }

    public static RedisProbeResult authRequired(long latencyMs, String message) {
        return new RedisProbeResult(true, true, false, null, latencyMs, message);
    }

    public static RedisProbeResult authFailed(long latencyMs, String message) {
        return new RedisProbeResult(true, false, true, null, latencyMs, message);
    }

    public static RedisProbeResult unreachable(String message) {
        return new RedisProbeResult(false, false, false, null, -1, message);
    }
}
