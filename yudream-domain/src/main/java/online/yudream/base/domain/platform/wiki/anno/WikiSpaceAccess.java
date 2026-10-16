package online.yudream.base.domain.platform.wiki.anno;

import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceResource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 空间实例 ACL 声明式守卫（配合 {@code WikiSpaceAclEnforcementAspect}）。
 * <p>
 * 用于「服务实现不能改动、但必须接入实例 ACL」的空间入口：切面先按注解解析空间上下文，
 * 再调用应用层 {@code WikiSpaceAclAppService} 执行「全局权限码 + 实例 ACL」双闸门；
 * 解析不到空间或非成员一律拒绝（fail closed），绝不静默放行。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WikiSpaceAccess {

    /** 空间动作（READ/COMMENT/EDIT/MANAGE）。 */
    WikiSpaceAclAction action();

    /**
     * 全局权限码。留空表示该端点已自行完成全局授权（例如 API Key 检索权限校验），
     * 本注解只补实例 ACL；留空时不得再放宽任何全局校验。
     */
    String permission() default "";

    /** 空间上下文来源。 */
    WikiSpaceResource resource() default WikiSpaceResource.SPACE;

    /** 承载 ID 的方法参数名；留空按 resource 约定（spaceId/sourceId/taskId/reviewId/nodeId）。 */
    String idParam() default "";
}
