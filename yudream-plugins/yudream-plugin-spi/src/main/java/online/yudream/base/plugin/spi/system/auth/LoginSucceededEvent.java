package online.yudream.base.plugin.spi.system.auth;

import java.time.Instant;
import java.util.Map;

/**
 * 用户登录成功事件（只通知，不影响登录流程）。
 * userId 为字符串形式的长整型 ID，禁止按 number 解析；
 * account 为用户登录时输入的账号原文。
 * 事件在登录事务提交后派发，监听器异常被隔离记录，不会导致登录失败。
 */
public record LoginSucceededEvent(String userId, String account,
                                  Map<String, Object> attributes, Instant occurredAt) {

    public LoginSucceededEvent {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
