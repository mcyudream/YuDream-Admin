package online.yudream.base.plugin.spi.system.auth;

import java.util.Map;

/**
 * 待核验的注册主体。username/email 来自注册请求；
 * attributes 为可扩展兜底字段，宿主只增不改不删。
 */
public record VerificationSubject(String username, String email, Map<String, Object> attributes) {

    public VerificationSubject {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
