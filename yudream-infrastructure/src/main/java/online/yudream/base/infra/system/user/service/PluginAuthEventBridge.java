package online.yudream.base.infra.system.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.user.event.LoginSucceededDomainEvent;
import online.yudream.base.domain.system.user.event.UserRegisteredDomainEvent;
import online.yudream.base.plugin.spi.system.auth.AuthEventListener;
import online.yudream.base.plugin.spi.system.auth.LoginSucceededEvent;
import online.yudream.base.plugin.spi.system.auth.UserRegisteredEvent;
import online.yudream.base.plugin.spi.system.extension.PluginExtensionQuery;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * 认证域事件桥：把应用层发布的注册/登录领域事件转换为插件 SPI 通知事件，
 * 在源事务提交后同步派发给所有 AuthEventListener 扩展。
 * 监听器按插件注册优先级排序，逐插件隔离异常（fail-open），不影响源用例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginAuthEventBridge {

    private final PluginExtensionQuery pluginExtensionQuery;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserRegistered(UserRegisteredDomainEvent event) {
        UserRegisteredEvent spiEvent = new UserRegisteredEvent(
                String.valueOf(event.userId()), event.username(), event.email(), Map.of(), event.occurredAt());
        for (AuthEventListener listener : pluginExtensionQuery.extensions(AuthEventListener.class)) {
            try {
                listener.onUserRegistered(spiEvent);
            } catch (RuntimeException e) {
                log.warn("插件注册成功事件监听异常: userId={}, listener={}", event.userId(), listener.getClass().getName(), e);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLoginSucceeded(LoginSucceededDomainEvent event) {
        LoginSucceededEvent spiEvent = new LoginSucceededEvent(
                String.valueOf(event.userId()), event.account(), Map.of(), event.occurredAt());
        for (AuthEventListener listener : pluginExtensionQuery.extensions(AuthEventListener.class)) {
            try {
                listener.onLoginSucceeded(spiEvent);
            } catch (RuntimeException e) {
                log.warn("插件登录成功事件监听异常: userId={}, listener={}", event.userId(), listener.getClass().getName(), e);
            }
        }
    }
}
