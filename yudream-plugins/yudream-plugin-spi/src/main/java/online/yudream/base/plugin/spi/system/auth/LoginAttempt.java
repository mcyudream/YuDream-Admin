package online.yudream.base.plugin.spi.system.auth;

import java.util.Map;

/**
 * 一次登录尝试的快照。account 是用户输入的账号原文（用户名或邮箱），
 * 此时凭据尚未校验，实现不得据此认定用户身份。
 */
public record LoginAttempt(String account, Map<String, Object> attributes) {

    public LoginAttempt {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
