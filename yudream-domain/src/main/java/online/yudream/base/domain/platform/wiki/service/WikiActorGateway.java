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

    /**
     * 指定用户的认证主体快照，按该用户持久化的部门/角色上下文解析。
     * <p>
     * 供安全上下文不可用的执行线程使用（如 AI 运行线程里的 {@code wiki.search} 工具）：
     * 主体仍来自服务端持久状态与已认证的用户 ID，绝不接受请求参数里的角色/权限声明；
     * 用户或角色任一不满足启用条件时返回 null，调用方按 fail closed 处理。
     */
    WikiActor actorFor(Long userId);

    /** 当前主体是否持有全局权限码（含超级管理员既有 {@code *} 语义）。 */
    boolean hasPermission(String permissionCode);
}
