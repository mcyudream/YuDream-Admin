package online.yudream.base.domain.platform.satori.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;

import java.net.URI;
import java.net.URISyntaxException;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SatoriConnection extends BaseDomain {

    private String name;
    private String baseUrl;
    @ToString.Exclude
    private String token;
    private boolean enabled;

    public static SatoriConnection create(String name, String baseUrl, String token) {
        return SatoriConnection.builder()
                .name(required(name, "连接名称不能为空"))
                .baseUrl(normalizeBaseUrl(baseUrl))
                .token(required(token, "连接令牌不能为空"))
                .enabled(true)
                .build();
    }

    public void update(String name, String baseUrl, String token) {
        this.name = required(name, "连接名称不能为空");
        this.baseUrl = normalizeBaseUrl(baseUrl);
        if (token != null && !token.isBlank()) {
            this.token = token.trim();
        }
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public boolean enabled() {
        return enabled;
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = required(value, "Satori 地址不能为空");
        try {
            URI uri = new URI(normalized);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getQuery() == null && uri.getFragment() == null) {
                String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
                return new URI(uri.getScheme().toLowerCase(), uri.getUserInfo(), uri.getHost().toLowerCase(), uri.getPort(),
                        path.isEmpty() ? null : path, null, null).toString();
            }
        } catch (URISyntaxException ignored) {
            // Use the domain error below so callers do not depend on URI parser details.
        }
        throw new BizException("Satori 地址必须是有效的 HTTP 地址");
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
