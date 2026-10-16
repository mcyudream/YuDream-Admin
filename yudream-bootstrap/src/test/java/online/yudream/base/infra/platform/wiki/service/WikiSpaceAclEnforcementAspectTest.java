package online.yudream.base.infra.platform.wiki.service;

import online.yudream.base.application.platform.wiki.service.WikiSpaceAclAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.anno.WikiSpaceAccess;
import online.yudream.base.domain.platform.wiki.aggregate.WikiIngestTask;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiReviewItem;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceResource;
import online.yudream.base.domain.platform.wiki.repo.WikiIngestTaskRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiReviewItemRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.system.security.service.ApiKeyAuthenticationContext;
import online.yudream.base.domain.system.security.valobj.ApiKeyAuthentication;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 声明式空间 ACL 守卫：空间上下文解析、资源反查、解析失败即拒绝、全局码留空时只做实例 ACL。
 */
class WikiSpaceAclEnforcementAspectTest {

    private final WikiSpaceAclAppService access = mock(WikiSpaceAclAppService.class);
    private final WikiSpaceRepo spaces = mock(WikiSpaceRepo.class);
    private final WikiSourceRepo sources = mock(WikiSourceRepo.class);
    private final WikiIngestTaskRepo ingestTasks = mock(WikiIngestTaskRepo.class);
    private final WikiReviewItemRepo reviewItems = mock(WikiReviewItemRepo.class);
    private final WikiNodeRepo nodes = mock(WikiNodeRepo.class);
    private final WikiSpaceAclEnforcementAspect aspect = new WikiSpaceAclEnforcementAspect(access, spaces, sources,
            ingestTasks, reviewItems, nodes);

    @BeforeEach
    void authenticateApiKey() {
        ApiKeyAuthenticationContext.set(new ApiKeyAuthentication(1L, 100L, List.of("*")));
    }

    @AfterEach
    void clearAuthentication() {
        ApiKeyAuthenticationContext.clear();
    }

    @Test
    void spaceIdParameterIsEnforcedWithDeclaredActionAndPermission() throws Throwable {
        ProceedingJoinPoint point = point("list", new Class<?>[]{Long.class}, new Object[]{10L});

        Object result = aspect.around(point, annotation(WikiSpaceAclAction.READ, "platform:wiki:view",
                WikiSpaceResource.SPACE, ""));

        assertThat(result).isEqualTo("joined");
        verify(access).requireSpaceAcl(10L, WikiSpaceAclAction.READ);
    }

    @Test
    void sourceDeleteResolvesOwningSpaceAndDeniesWhenSourceMissing() throws Throwable {
        WikiSource source = WikiSource.file(77L, "/", "a.pdf", "A", "application/pdf", null, "hash");
        source.setId(5L);
        when(sources.findById(5L)).thenReturn(Optional.of(source));
        ProceedingJoinPoint point = point("delete", new Class<?>[]{Long.class}, new Object[]{5L});

        aspect.around(point, annotation(WikiSpaceAclAction.EDIT, "platform:wiki:delete", WikiSpaceResource.SOURCE, "id"));

        verify(access).requireSpaceAcl(77L, WikiSpaceAclAction.EDIT);

        // 资源不存在：无法解析空间上下文，必须拒绝且不进入业务方法
        when(sources.findById(9L)).thenReturn(Optional.empty());
        ProceedingJoinPoint missing = point("delete", new Class<?>[]{Long.class}, new Object[]{9L});
        assertThatThrownBy(() -> aspect.around(missing,
                annotation(WikiSpaceAclAction.EDIT, "platform:wiki:delete", WikiSpaceResource.SOURCE, "id")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法解析知识库上下文");
        verify(missing, never()).proceed();
    }

    @Test
    void ingestTaskAndReviewItemResolveOwningSpace() throws Throwable {
        WikiIngestTask task = WikiIngestTask.create(21L, 3L,
                online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskType.INGEST, null, 1L);
        task.setId(31L);
        when(ingestTasks.findById(31L)).thenReturn(Optional.of(task));
        aspect.around(point("cancel", new Class<?>[]{Long.class}, new Object[]{31L}),
                annotation(WikiSpaceAclAction.EDIT, "platform:wiki:publish", WikiSpaceResource.INGEST_TASK, "id"));
        verify(access).requireSpaceAcl(21L, WikiSpaceAclAction.EDIT);

        WikiReviewItem item = WikiReviewItem.create(22L, 3L,
                online.yudream.base.domain.platform.wiki.enumerate.WikiReviewItemType.FLAG, "t", "d", "a",
                List.of(), List.of());
        item.setId(41L);
        when(reviewItems.findById(41L)).thenReturn(Optional.of(item));
        aspect.around(point("resolve", new Class<?>[]{Long.class}, new Object[]{41L}),
                annotation(WikiSpaceAclAction.EDIT, "platform:wiki:edit", WikiSpaceResource.REVIEW, "id"));
        verify(access).requireSpaceAcl(22L, WikiSpaceAclAction.EDIT);
    }

    @Test
    void nodeProgressResolvesOwningSpace() throws Throwable {
        WikiNode node = WikiNode.page(33L, null, "页面", "page", 0);
        node.setId(51L);
        when(nodes.findById(51L)).thenReturn(Optional.of(node));

        aspect.around(point("events", new Class<?>[]{Long.class}, new Object[]{51L}),
                annotation(WikiSpaceAclAction.READ, "platform:wiki:view", WikiSpaceResource.NODE, "nodeId"));

        verify(access).requireSpaceAcl(33L, WikiSpaceAclAction.READ);
    }

    @Test
    void slugIsResolvedFromPathVariableAndFromRequestBody() throws Throwable {
        WikiSpace space = WikiSpace.create("库", "demo");
        space.setId(44L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));

        aspect.around(point("search", new Class<?>[]{String.class, Object.class}, new Object[]{"demo", new Object()}),
                annotation(WikiSpaceAclAction.READ, "platform:wiki:view", WikiSpaceResource.SPACE_SLUG, ""));
        verify(access).requireSpaceAcl(44L, WikiSpaceAclAction.READ);

        WikiSearchBody body = new WikiSearchBody("demo");
        aspect.around(point("searchTest", new Class<?>[]{Object.class}, new Object[]{body}),
                annotation(WikiSpaceAclAction.READ, "platform:wiki:view", WikiSpaceResource.SPACE_SLUG, ""));
        verify(access, org.mockito.Mockito.times(2)).requireSpaceAcl(44L, WikiSpaceAclAction.READ);
    }

    @Test
    void blankSlugFailsClosed() throws Throwable {
        WikiSearchBody body = new WikiSearchBody("   ");
        ProceedingJoinPoint point = point("searchTest", new Class<?>[]{Object.class}, new Object[]{body});

        assertThatThrownBy(() -> aspect.around(point,
                annotation(WikiSpaceAclAction.READ, "platform:wiki:view", WikiSpaceResource.SPACE_SLUG, "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法解析知识库上下文");
        verify(point, never()).proceed();
    }

    @Test
    void blankPermissionOnlyAppliesInstanceAcl() throws Throwable {
        WikiSpace space = WikiSpace.create("库", "demo");
        space.setId(44L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        ProceedingJoinPoint point = point("openSearch", new Class<?>[]{String.class}, new Object[]{"demo"});

        aspect.around(point, annotation(WikiSpaceAclAction.READ, "", WikiSpaceResource.SPACE_SLUG, ""));

        // 全局授权由端点的 API Key 校验完成（permission 留空），切面只补实例 ACL
        verify(access).requireSpaceAcl(44L, WikiSpaceAclAction.READ);
    }

    @Test
    void openSearchWithoutApiKeyScopeIsRejectedBeforeSpaceLookup() throws Throwable {
        ApiKeyAuthenticationContext.set(new ApiKeyAuthentication(1L, 100L, List.of("platform:wiki:view")));
        ProceedingJoinPoint point = point("openSearch", new Class<?>[]{String.class}, new Object[]{"demo"});

        assertThatThrownBy(() -> aspect.around(point,
                annotation(WikiSpaceAclAction.READ, "", WikiSpaceResource.SPACE_SLUG, "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("API Key 无知识库检索权限");
        org.mockito.Mockito.verifyNoInteractions(access, spaces);
        verify(point, never()).proceed();
    }

    private static WikiSpaceAccess annotation(WikiSpaceAclAction action, String permission, WikiSpaceResource resource,
                                              String idParam) {
        return new WikiSpaceAccess() {
            @Override
            public WikiSpaceAclAction action() {
                return action;
            }

            @Override
            public String permission() {
                return permission;
            }

            @Override
            public WikiSpaceResource resource() {
                return resource;
            }

            @Override
            public String idParam() {
                return idParam;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return WikiSpaceAccess.class;
            }
        };
    }

    private static ProceedingJoinPoint point(String methodName, Class<?>[] parameterTypes, Object[] args)
            throws Throwable {
        Method method = Fixture.class.getMethod(methodName, parameterTypes);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(args);
        when(point.proceed()).thenReturn("joined");
        return point;
    }

    /** 供参数名解析使用（编译开启 -parameters，参数名保留）。 */
    public static class Fixture {
        public String list(Long spaceId) {
            return "joined";
        }

        public String delete(Long id) {
            return "joined";
        }

        public String cancel(Long id) {
            return "joined";
        }

        public String resolve(Long id) {
            return "joined";
        }

        public String events(Long nodeId) {
            return "joined";
        }

        public String search(@PathVariable String spaceSlug, Object request) {
            return "joined";
        }

        public String searchTest(Object request) {
            return "joined";
        }

        public String openSearch(String spaceSlug) {
            return "joined";
        }
    }

    /** 请求体形态：切片通过 getSpaceSlug() 读取空间上下文。 */
    public static class WikiSearchBody {
        private final String spaceSlug;

        public WikiSearchBody(String spaceSlug) {
            this.spaceSlug = spaceSlug;
        }

        public String getSpaceSlug() {
            return spaceSlug;
        }
    }
}
