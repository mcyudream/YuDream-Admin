package online.yudream.base.domain.system.integration.valobj;

/**
 * 集成配置探测结果（测试邮件发送 / 对象存储连通性）。
 */
public record IntegrationProbeResult(boolean ok, String message) {

    public static IntegrationProbeResult ok(String message) {
        return new IntegrationProbeResult(true, message);
    }

    public static IntegrationProbeResult fail(String message) {
        return new IntegrationProbeResult(false, message);
    }
}
