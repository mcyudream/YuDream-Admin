package online.yudream.base.interfaces.platform.wiki;

import online.yudream.base.application.platform.wiki.service.WikiAccessCoverage;
import online.yudream.base.application.platform.wiki.service.WikiCommentAppService;
import online.yudream.base.application.platform.wiki.service.WikiCommentManageAppService;
import online.yudream.base.application.platform.wiki.service.WikiSpaceAclAppService;
import online.yudream.base.application.platform.wiki.service.WikiSpaceCoverAppService;
import online.yudream.base.application.platform.wiki.service.WikiSpaceMemberAppService;
import online.yudream.base.domain.platform.wiki.anno.WikiSpaceAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 受限 ACL 覆盖守卫：RESTRICTED 处于可暴露状态时，平台侧每个空间入口都必须
 * 经应用层双闸门（注入 ACL 门禁服务）或 {@code @WikiSpaceAccess} 声明式守卫，
 * 或落在显式登记的例外清单里。任何新增的「裸」空间入口都会让本测试失败，
 * 避免出现「看起来受限、实际可绕过」的假安全。
 */
class WikiSpaceAclCoverageTest {

    /** 应用层 ACL 门禁服务：注入其中任一即代表该控制器的用例已过实例 ACL。 */
    private static final Set<Class<?>> GATE_SERVICES = Set.of(
            WikiSpaceAclAppService.class,
            WikiSpaceCoverAppService.class,
            WikiSpaceMemberAppService.class,
            WikiCommentManageAppService.class,
            WikiCommentAppService.class);

    /** 与 {@link WikiAccessCoverage#DOCUMENTED_EXCEPTIONS} 对应的代码级例外。 */
    private static final Set<String> DOCUMENTED_EXCEPTIONS = Set.of("WikiMigrationController#importArchive");

    private static final String CONTROLLER_PACKAGE =
            "online.yudream.base.interfaces.platform.wiki.controller";

    @Test
    void restrictedExposureRequiresEveryPlatformEntryToBeGuarded() throws Exception {
        assumeTrue(WikiAccessCoverage.RESTRICTED_EXPOSURE_READY,
                "受限 ACL 未开放时不做覆盖断言");

        List<String> unguarded = new ArrayList<>();
        for (Class<?> controller : platformWikiControllers()) {
            if (hasGateService(controller)) {
                continue;
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (!mapped(method) || method.isAnnotationPresent(WikiSpaceAccess.class)) {
                    continue;
                }
                if (DOCUMENTED_EXCEPTIONS.contains(controller.getSimpleName() + "#" + method.getName())) {
                    continue;
                }
                unguarded.add(controller.getSimpleName() + "#" + method.getName());
            }
        }

        assertThat(unguarded)
                .as("以下平台空间入口既未接入实例 ACL，也不在例外清单中")
                .isEmpty();
    }

    @Test
    void documentedExceptionsStayAccurate() {
        assertThat(WikiAccessCoverage.DOCUMENTED_EXCEPTIONS).isNotEmpty();
        assertThat(DOCUMENTED_EXCEPTIONS).contains("WikiMigrationController#importArchive");
    }

    private static List<Class<?>> platformWikiControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (isPlatformWikiController(type)) {
                controllers.add(type);
            }
        }
        return controllers;
    }

    /** 仅覆盖平台侧 Wiki 入口：/api/platform/wiki（含子路径）。 */
    private static boolean isPlatformWikiController(Class<?> type) {
        RequestMapping mapping = type.getAnnotation(RequestMapping.class);
        if (mapping == null) {
            return false;
        }
        for (String value : mapping.value()) {
            if (value.startsWith("/api/platform/wiki")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasGateService(Class<?> controller) {
        for (Field field : controller.getDeclaredFields()) {
            if (GATE_SERVICES.contains(field.getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean mapped(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(RequestMapping.class);
    }
}
