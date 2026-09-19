package online.yudream.base.application.common.net;

import online.yudream.base.domain.common.exception.BizException;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 出站请求目标防护：服务端外呼前统一校验协议与解析出的 IP，默认拒绝回环、私网、
 * 链路本地（含云元数据 169.254.169.254）、CGNAT、ULA 及其他保留网段。
 * <p>
 * 校验发生在发起请求前；跟随重定向时必须对每一跳重新校验
 * （{@link #validateRedirect}），因此共享 HttpClient 应配置为不自动跟随重定向。
 * DNS 解析与真实建连存在理论上的重绑定窗口，部署侧可通过网络隔离进一步收紧。
 */
public final class OutboundUrlGuard {

    private OutboundUrlGuard() {
    }

    /** 校验字符串 URL，返回可直接用于构建请求的 URI。 */
    public static URI validate(String rawUrl, String purpose, boolean allowPrivateNetwork) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BizException(purpose + "地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new BizException(purpose + "地址格式不正确");
        }
        return validate(uri, purpose, allowPrivateNetwork);
    }

    /** 校验已解析的 URI。 */
    public static URI validate(URI uri, String purpose, boolean allowPrivateNetwork) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BizException(purpose + "仅支持 http/https 地址");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BizException(purpose + "缺少主机地址");
        }
        if (uri.getUserInfo() != null) {
            throw new BizException(purpose + "不允许携带用户凭据信息");
        }
        assertHostAllowed(uri.getHost(), purpose, allowPrivateNetwork);
        return uri;
    }

    /** 重定向跳转校验：基于当前 URI 解析 Location 后重新执行完整校验。 */
    public static URI validateRedirect(URI current, String location, String purpose, boolean allowPrivateNetwork) {
        if (location == null || location.isBlank()) {
            throw new BizException(purpose + "重定向地址缺失");
        }
        return validate(current.resolve(location), purpose, allowPrivateNetwork);
    }

    private static void assertHostAllowed(String host, String purpose, boolean allowPrivateNetwork) {
        if (allowPrivateNetwork) {
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new BizException(purpose + "主机无法解析");
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw new BizException(purpose + "不允许指向内网或保留地址");
            }
        }
    }

    private static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isMulticastAddress() || address.isSiteLocalAddress()) {
            return true;
        }
        if (address instanceof Inet4Address) {
            return isBlockedIpv4(address.getAddress());
        }
        return isBlockedIpv6(address.getAddress());
    }

    private static boolean isBlockedIpv4(byte[] bytes) {
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        int third = bytes[2] & 0xFF;
        if (first == 0 || first >= 240) {
            return true;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return true;
        }
        if (first == 192 && second == 0 && (third == 0 || third == 2)) {
            return true;
        }
        if (first == 198 && (second == 18 || second == 19)) {
            return true;
        }
        if (first == 198 && second == 51 && third == 100) {
            return true;
        }
        return first == 203 && second == 0 && third == 113;
    }

    private static boolean isBlockedIpv6(byte[] bytes) {
        int first = bytes[0] & 0xFF;
        if ((first & 0xFE) == 0xFC) {
            return true;
        }
        if (first == 0x20 && (bytes[1] & 0xFF) == 0 && (bytes[2] & 0xFF) == 0 && (bytes[3] & 0xFF) == 0) {
            int fourth = bytes[3] & 0xFF;
            return fourth == 0x01 || fourth == 0x02 || (fourth >= 0x10 && fourth <= 0x1F);
        }
        return false;
    }
}
