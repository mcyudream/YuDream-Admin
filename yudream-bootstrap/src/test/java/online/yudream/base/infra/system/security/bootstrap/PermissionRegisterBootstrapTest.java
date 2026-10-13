package online.yudream.base.infra.system.security.bootstrap;

import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.domain.system.user.service.PermissionDomainService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ClassUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionRegisterBootstrapTest {

    @Test
    void scansImplementationMethodAnnotationsBehindAJdkInterfaceProxy() {
        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(new ProxiedController());
        factory.setInterfaces(PermissionEndpoint.class);
        factory.setProxyTargetClass(false);
        Object proxy = factory.getProxy();
        assertTrue(AopUtils.isJdkDynamicProxy(proxy));
        assertEquals(proxy.getClass(), ClassUtils.getUserClass(proxy));
        ApplicationContext context = mock(ApplicationContext.class);
        PermissionDomainService service = mock(PermissionDomainService.class);
        when(context.getBeanDefinitionNames()).thenReturn(new String[]{"proxiedController"});
        when(context.getBean("proxiedController")).thenReturn(proxy);
        var scanned = new PermissionRegisterBootstrap(context, service).scanPermissions();
        assertEquals(1, scanned.size());
        assertEquals("system:role:edit", scanned.getFirst().code());
        assertEquals("编辑角色", scanned.getFirst().name());
        verifyNoInteractions(service);
    }

    @Test
    void readonlyScanKeepsAllEndpointMetadataAndRescanOnlySynchronizesTheRegistry() {
        ApplicationContext context = mock(ApplicationContext.class);
        PermissionDomainService service = mock(PermissionDomainService.class);
        when(context.getBeanDefinitionNames()).thenReturn(new String[]{"controller"});
        when(context.getBean("controller")).thenReturn(new Controller());
        PermissionRegisterBootstrap scanner = new PermissionRegisterBootstrap(context, service);
        var scanned = scanner.scanPermissions();
        assertEquals(1, scanned.size());
        assertEquals("查看角色", scanned.getFirst().name());
        assertTrue(scanned.getFirst().desc().contains("角色列表"));
        assertTrue(scanned.getFirst().desc().contains("角色选项"));
        verifyNoInteractions(service);
        scanner.rescan();
        verify(service).syncPermissions(scanned);
    }

    static class Controller {
        @PermissionRegister(code = "system:role:view", name = "查看角色", module = "系统管理", desc = "角色列表")
        public void list() {
        }
        @PermissionRegister(code = "system:role:view", name = "查看角色选项", module = "系统管理", desc = "角色选项")
        public void options() {
        }
    }

    interface PermissionEndpoint {
        void edit();
    }

    static class ProxiedController implements PermissionEndpoint {
        @Override
        @PermissionRegister(code = "system:role:edit", name = "编辑角色", module = "系统管理", desc = "实现类方法登记")
        public void edit() {
        }
    }
}
