package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.valobj.WikiActor;

/**
 * Wiki 认证主体端口：提供当前请求的认证主体与全局权限码校验。
 * <p>
 * 实现必须基于认证上下文（Sa-Token 会话 / API Key），禁止从请求参数推断身份；
 * 无法确认身份时返回 null / false，调用方按 fail closed 处理。
 */
public interface WikiActorGateway {

    /** 当前认证主体；未登录或身份不可信时返回 null。 */
    WikiActor currentActor();

    /** 当前主体是否持有全局权限码（含超级管理员既有 {@code *} 语义）。 */
    boolean hasPermission(String permissionCode);
}
