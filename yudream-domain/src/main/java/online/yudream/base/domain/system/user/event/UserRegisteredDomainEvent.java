package online.yudream.base.domain.system.user.event;

import java.time.Instant;

/**
 * 用户注册成功领域事件，由应用层在注册事务内发布，
 * 基础设施层桥接为插件 SPI 通知事件（事务提交后派发）。
 */
public record UserRegisteredDomainEvent(Long userId, String username, String email, Instant occurredAt) {

    public static UserRegisteredDomainEvent of(Long userId, String username, String email) {
        return new UserRegisteredDomainEvent(userId, username, email, Instant.now());
    }
}
