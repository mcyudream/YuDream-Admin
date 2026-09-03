package online.yudream.base.plugin.spi.system.auth;

import java.time.Instant;
import java.util.Map;

/**
 * 用户注册成功事件（只通知，不影响注册流程）。
 * userId 为字符串形式的长整型 ID，禁止按 number 解析。
 * 事件在注册事务提交后派发，监听器异常被隔离记录，不会回滚注册。
 */
public record UserRegisteredEvent(String userId, String username, String email,
                                  Map<String, Object> attributes, Instant occurredAt) {

    public UserRegisteredEvent {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
