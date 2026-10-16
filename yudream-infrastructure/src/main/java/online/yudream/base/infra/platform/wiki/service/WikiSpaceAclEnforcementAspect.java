package online.yudream.base.infra.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.service.WikiSpaceAclAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.anno.WikiSpaceAccess;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceResource;
import online.yudream.base.domain.platform.wiki.repo.WikiIngestTaskRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiReviewItemRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.system.security.service.ApiKeyAuthenticationContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * {@link WikiSpaceAccess} 切面：把空间实例 ACL 施加到既有空间入口上。
 * <p>
 * 解析不到空间上下文（资源不存在、ID 非法、slug 为空）一律拒绝，避免「字段没传就绕过 ACL」；
 * 空间解析只读仓储，不触碰被其它批次占用的服务实现。
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class WikiSpaceAclEnforcementAspect {

    private final WikiSpaceAclAppService accessAppService;
    private final WikiSpaceRepo spaceRepo;
    private final WikiSourceRepo sourceRepo;
    private final WikiIngestTaskRepo ingestTaskRepo;
    private final WikiReviewItemRepo reviewItemRepo;
    private final WikiNodeRepo nodeRepo;

    @Around("@annotation(wikiSpaceAccess)")
    public Object around(ProceedingJoinPoint point, WikiSpaceAccess wikiSpaceAccess) throws Throwable {
        if (!StringUtils.hasText(wikiSpaceAccess.permission())
                && wikiSpaceAccess.resource() == WikiSpaceResource.SPACE_SLUG) {
            String slug = resolveStringParam(point, "spaceSlug");
            if (!StringUtils.hasText(slug)
                    || (!ApiKeyAuthenticationContext.hasPermission("wiki:search:*")
                    && !ApiKeyAuthenticationContext.hasPermission("wiki:search:" + slug))) {
                throw new BizException("API Key 无知识库检索权限");
            }
        }
        Long spaceId = resolveSpaceId(point, wikiSpaceAccess);
        if (spaceId == null) {
            throw new BizException("无法解析知识库上下文，已拒绝该请求");
        }
        if (StringUtils.hasText(wikiSpaceAccess.permission())) {
            if (!ApiKeyAuthenticationContext.hasPermission(wikiSpaceAccess.permission())) {
                cn.dev33.satoken.stp.StpUtil.checkPermission(wikiSpaceAccess.permission());
            }
        }
        accessAppService.requireSpaceAcl(spaceId, wikiSpaceAccess.action());
        return point.proceed();
    }

    private Long resolveSpaceId(ProceedingJoinPoint point, WikiSpaceAccess access) {
        return switch (access.resource()) {
            case SPACE -> resolveByParam(point, paramName(access, "spaceId"));
            case SOURCE -> sourceSpaceId(resolveByParam(point, paramName(access, "sourceId")));
            case INGEST_TASK -> taskSpaceId(resolveByParam(point, paramName(access, "taskId")));
            case REVIEW -> reviewSpaceId(resolveByParam(point, paramName(access, "reviewId")));
            case NODE -> nodeSpaceId(resolveByParam(point, paramName(access, "nodeId")));
            case SPACE_SLUG -> slugSpaceId(point);
        };
    }

    private String paramName(WikiSpaceAccess access, String fallback) {
        return StringUtils.hasText(access.idParam()) ? access.idParam() : fallback;
    }

    /** 按参数名取 ID；参数名与请求参数名（-parameters 编译）一致，取不到即视为无法解析。 */
    private Long resolveByParam(ProceedingJoinPoint point, String name) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = point.getArgs();
        for (int index = 0; index < parameters.length; index++) {
            if (!name.equals(parameters[index].getName()) || index >= args.length) {
                continue;
            }
            Object value = args[index];
            if (value instanceof Long id) {
                return id;
            }
            if (value instanceof String text && StringUtils.hasText(text)) {
                try {
                    return Long.valueOf(text.trim());
                }
                catch (NumberFormatException error) {
                    return null;
                }
            }
            return null;
        }
        return null;
    }

    /** 公开/管理检索入口以 slug 定位空间：先取同名参数，再取请求体属性。 */
    private Long slugSpaceId(ProceedingJoinPoint point) {
        String slug = resolveStringParam(point, "spaceSlug");
        if (!StringUtils.hasText(slug)) {
            slug = resolveStringProperty(point, "spaceSlug");
        }
        if (!StringUtils.hasText(slug)) {
            return null;
        }
        return spaceRepo.findBySlug(slug.trim()).map(space -> space.getId()).orElse(null);
    }

    private String resolveStringParam(ProceedingJoinPoint point, String name) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = point.getArgs();
        for (int index = 0; index < parameters.length; index++) {
            if (name.equals(parameters[index].getName()) && index < args.length && args[index] instanceof String text) {
                return text;
            }
        }
        return null;
    }

    private String resolveStringProperty(ProceedingJoinPoint point, String name) {
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (Object argument : point.getArgs()) {
            if (argument == null || argument instanceof String || argument instanceof Number) {
                continue;
            }
            try {
                Method method = argument.getClass().getMethod(getter);
                Object value = method.invoke(argument);
                if (value instanceof String text) {
                    return text;
                }
            }
            catch (ReflectiveOperationException ignored) {
                // 该参数没有该属性：继续看下一个参数
            }
        }
        return null;
    }

    private Long sourceSpaceId(Long sourceId) {
        if (sourceId == null) {
            return null;
        }
        return sourceRepo.findById(sourceId).map(source -> source.getSpaceId()).orElse(null);
    }

    private Long taskSpaceId(Long taskId) {
        if (taskId == null) {
            return null;
        }
        return ingestTaskRepo.findById(taskId).map(task -> task.getSpaceId()).orElse(null);
    }

    private Long reviewSpaceId(Long reviewId) {
        if (reviewId == null) {
            return null;
        }
        return reviewItemRepo.findById(reviewId).map(item -> item.getSpaceId()).orElse(null);
    }

    private Long nodeSpaceId(Long nodeId) {
        if (nodeId == null) {
            return null;
        }
        return nodeRepo.findById(nodeId).map(node -> node.getSpaceId()).orElse(null);
    }
}
