package online.yudream.base.domain.system.user.event;

import java.time.Instant;

/**
 * 用户登录成功领域事件，由应用层在登录事务内发布，
 * 基础设施层桥接为插件 SPI 通知事件（事务提交后派发）。
 */
public record LoginSucceededDomainEvent(Long userId, String account, Instant occurredAt) {

    public static LoginSucceededDomainEvent of(Long userId, String account) {
        return new LoginSucceededDomainEvent(userId, account, Instant.now());
    }
}
