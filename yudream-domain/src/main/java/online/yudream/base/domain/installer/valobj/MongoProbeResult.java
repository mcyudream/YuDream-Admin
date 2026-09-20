package online.yudream.base.domain.installer.valobj;

/**
 * MongoDB 探测结果：reachable 表示网络可达；authRequired 表示服务器要求认证但未提供凭据；
 * authFailed 表示凭据被拒绝。
 */
public record MongoProbeResult(
        boolean reachable,
        boolean authRequired,
        boolean authFailed,
        String version,
        long latencyMs,
        String message
) {

    public static MongoProbeResult ok(long latencyMs, String version) {
        return new MongoProbeResult(true, false, false, version, latencyMs, "连接成功");
    }

    public static MongoProbeResult authRequired(long latencyMs, String message) {
        return new MongoProbeResult(true, true, false, null, latencyMs, message);
    }

    public static MongoProbeResult authFailed(long latencyMs, String message) {
        return new MongoProbeResult(true, false, true, null, latencyMs, message);
    }

    public static MongoProbeResult unreachable(String message) {
        return new MongoProbeResult(false, false, false, null, -1, message);
    }
}
