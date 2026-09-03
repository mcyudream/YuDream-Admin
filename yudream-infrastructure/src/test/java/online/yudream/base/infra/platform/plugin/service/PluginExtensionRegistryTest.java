package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.system.auth.AuthEventListener;
import online.yudream.base.plugin.spi.system.auth.RegisterAttempt;
import online.yudream.base.plugin.spi.system.auth.ExtensionVeto;
import online.yudream.base.plugin.spi.system.auth.RegisterInterceptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginExtensionRegistryTest {

    private final PluginExtensionRegistry registry = new PluginExtensionRegistry();

    @Test
    void sortsExtensionsByPriorityThenRegistrationSequence() {
        RegisterInterceptor later = attempt -> ExtensionVeto.allow();
        RegisterInterceptor earlier = attempt -> ExtensionVeto.allow();
        RegisterInterceptor middle = attempt -> ExtensionVeto.allow();
        registry.register("plugin-b", RegisterInterceptor.class, later, 10);
        registry.register("plugin-a", RegisterInterceptor.class, earlier, -5);
        registry.register("plugin-c", RegisterInterceptor.class, middle, 0);

        List<RegisterInterceptor> extensions = registry.extensions(RegisterInterceptor.class);

        assertEquals(List.of(earlier, middle, later), extensions);
    }

    @Test
    void removesExtensionWhenRegistrationHandleClosed() throws Exception {
        RegisterInterceptor interceptor = attempt -> ExtensionVeto.allow();
        AutoCloseable handle = registry.register("plugin-a", RegisterInterceptor.class, interceptor, 0);
        assertEquals(1, registry.extensions(RegisterInterceptor.class).size());

        handle.close();

        assertTrue(registry.extensions(RegisterInterceptor.class).isEmpty());
    }

    @Test
    void queriesAreScopedByExtensionPoint() {
        registry.register("plugin-a", RegisterInterceptor.class, attempt -> ExtensionVeto.allow(), 0);

        assertTrue(registry.extensions(AuthEventListener.class).isEmpty());
        assertEquals(1, registry.extensions(RegisterInterceptor.class).size());
    }

    @Test
    void closingTwiceKeepsOtherPluginRegistrations() throws Exception {
        RegisterInterceptor first = attempt -> ExtensionVeto.allow();
        RegisterInterceptor second = attempt -> ExtensionVeto.allow();
        AutoCloseable handle = registry.register("plugin-a", RegisterInterceptor.class, first, 0);
        registry.register("plugin-b", RegisterInterceptor.class, second, 0);

        handle.close();
        handle.close();

        assertEquals(List.of(second), registry.extensions(RegisterInterceptor.class));
    }
}
