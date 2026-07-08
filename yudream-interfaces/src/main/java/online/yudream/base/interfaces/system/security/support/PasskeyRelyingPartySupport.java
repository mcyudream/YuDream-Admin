package online.yudream.base.interfaces.system.security.support;

import jakarta.servlet.http.HttpServletRequest;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.valobj.PasskeyRelyingPartyContext;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

public class PasskeyRelyingPartySupport {

    private static final String DEFAULT_RP_NAME = "YuDream Admin";

    private PasskeyRelyingPartySupport() {
    }

    public static PasskeyRelyingPartyContext from(HttpServletRequest request) {
        String origin = requestOrigin(request);
        String host = host(origin);
        return new PasskeyRelyingPartyContext(host, origin, DEFAULT_RP_NAME);
    }

    private static String requestOrigin(HttpServletRequest request) {
        String browserOrigin = firstHeaderValue(request.getHeader("Origin"));
        if (StringUtils.hasText(browserOrigin)) {
            return normalizeOrigin(browserOrigin);
        }
        String proto = firstHeaderValue(request.getHeader("X-Forwarded-Proto"));
        String host = firstHeaderValue(request.getHeader("X-Forwarded-Host"));
        if (!StringUtils.hasText(host)) {
            host = firstHeaderValue(request.getHeader("Host"));
        }
        if (!StringUtils.hasText(proto)) {
            proto = localHost(host) ? "http" : "https";
        }
        return normalizeOrigin(proto + "://" + host);
    }

    private static String normalizeOrigin(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw new BizException("Passkey Origin 无效");
            }
            String origin = uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT);
            if (uri.getPort() > 0) {
                origin += ":" + uri.getPort();
            }
            return origin;
        }
        catch (IllegalArgumentException e) {
            throw new BizException("Passkey Origin 无效");
        }
    }

    private static String host(String origin) {
        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                throw new BizException("Passkey RP ID 无效");
            }
            return host.toLowerCase(Locale.ROOT);
        }
        catch (IllegalArgumentException e) {
            throw new BizException("Passkey RP ID 无效");
        }
    }

    private static String firstHeaderValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.split(",")[0].trim();
    }

    private static boolean localHost(String host) {
        if (!StringUtils.hasText(host)) {
            return true;
        }
        String value = host.toLowerCase(Locale.ROOT);
        int portIndex = value.indexOf(':');
        if (portIndex > -1) {
            value = value.substring(0, portIndex);
        }
        return "localhost".equals(value) || "127.0.0.1".equals(value) || "::1".equals(value);
    }
}
