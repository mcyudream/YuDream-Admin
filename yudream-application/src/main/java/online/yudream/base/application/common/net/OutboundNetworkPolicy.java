package online.yudream.base.application.common.net;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * 出站目标策略：统一读取部署级开关
 * {@code yudream.security.outbound.allow-private-network}。
 * <p>
 * 默认 {@code false}：所有服务端外呼仅允许公网目标；自托管集成（内网 SearXNG、
 * kkFileView、内网市场源、内网 HTTP 连接器等）可在部署配置中显式放开。
 */
@Component
public class OutboundNetworkPolicy {

    private final boolean allowPrivateNetwork;

    public OutboundNetworkPolicy(
            @Value("${yudream.security.outbound.allow-private-network:false}") boolean allowPrivateNetwork) {
        this.allowPrivateNetwork = allowPrivateNetwork;
    }

    public boolean allowPrivateNetwork() {
        return allowPrivateNetwork;
    }

    /** 校验外呼目标，返回可直接用于构建请求的 URI。 */
    public URI validate(String rawUrl, String purpose) {
        return OutboundUrlGuard.validate(rawUrl, purpose, allowPrivateNetwork);
    }

    /** 按调用方给定的内网放行策略校验外呼目标（内容驱动抓取应固定传 false）。 */
    public URI validate(String rawUrl, String purpose, boolean allowPrivateNetwork) {
        return OutboundUrlGuard.validate(rawUrl, purpose, allowPrivateNetwork);
    }

    /** 校验重定向跳转目标。 */
    public URI validateRedirect(URI current, String location, String purpose) {
        return OutboundUrlGuard.validateRedirect(current, location, purpose, allowPrivateNetwork);
    }
}
