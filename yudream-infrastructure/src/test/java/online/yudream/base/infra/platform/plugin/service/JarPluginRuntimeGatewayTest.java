package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.service.AgentRuntimeApplicationRegistry;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.enumerate.PluginLifecycleAction;
import online.yudream.base.domain.platform.plugin.event.PluginLifecycleEvent;
import online.yudream.base.domain.platform.plugin.valobj.PluginRuntimeAssets;
import online.yudream.base.plugin.spi.annotation.PluginCommand;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.command.PluginCommandContext;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipError;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarPluginRuntimeGatewayTest {

    @TempDir
    Path pluginDir;

    @Test
    void enableFailureWithErrorRollsBackContributionsSoRetryDoesNotReportDuplicateCommand() throws IOException {
        Path jar = writePluginJar("error-plugin.jar", "error-plugin", ErrorOnEnablePlugin.class.getName());
        JarPluginRuntimeGateway gateway = newGateway();
        PluginModule module = module("error-plugin", jar);

        // 模拟损坏 JAR 触发的 ZipError（Error 而非 RuntimeException）：必须同样回滚指令等已注册贡献
        assertThrows(ZipError.class, () -> gateway.enable(module));
        assertFalse(gateway.enabled("error-plugin"));
        assertTrue(gateway.commands().isEmpty());

        // 修复 JAR 后重试不能再报“插件指令编码重复”，而是再次走到真正的失败点
        assertThrows(ZipError.class, () -> gateway.enable(module));
        gateway.unload("error-plugin");
    }

    @Test
    void enableFailureWithRuntimeExceptionStillRollsBack() throws IOException {
        Path jar = writePluginJar("failing-plugin.jar", "failing-plugin", FailingOnEnablePlugin.class.getName());
        JarPluginRuntimeGateway gateway = newGateway();
        PluginModule module = module("failing-plugin", jar);

        assertThrows(BizException.class, () -> gateway.enable(module));
        assertThrows(BizException.class, () -> gateway.enable(module));
        assertTrue(gateway.commands().isEmpty());
        gateway.unload("failing-plugin");
    }

    @Test
    void successfulEnableRegistersCommandsAndDisableClearsThem() throws IOException {
        Path jar = writePluginJar("healthy-plugin.jar", "healthy-plugin", HealthyPlugin.class.getName());
        JarPluginRuntimeGateway gateway = newGateway();
        PluginModule module = module("healthy-plugin", jar);

        gateway.enable(module);
        assertTrue(gateway.enabled("healthy-plugin"));
        assertEquals(1, gateway.commands().size());
        assertEquals("healthy-cmd", gateway.commands().get(0).code());

        gateway.disable("healthy-plugin");
        assertFalse(gateway.enabled("healthy-plugin"));
        assertTrue(gateway.commands().isEmpty());

        // 禁用后重新启用不得报指令重复
        assertDoesNotThrow(() -> gateway.enable(module));
        assertEquals(1, gateway.commands().size());
        gateway.unload("healthy-plugin");
    }

    @Test
    void loadFailureDoesNotWedgeGatewayAndReleasesJarFile() throws IOException {
        Path jar = writePluginJar("load-fail.jar", "load-fail", FailingOnLoadPlugin.class.getName());
        JarPluginRuntimeGateway gateway = newGateway();

        assertThrows(BizException.class, () -> gateway.load(module("load-fail", jar)));
        assertFalse(gateway.loaded("load-fail"));

        // ClassLoader 必须已释放，否则 Windows 下 JAR 文件被锁定无法替换
        assertDoesNotThrow(() -> Files.delete(jar));

        Path healthyJar = writePluginJar("load-fail-fixed.jar", "load-fail", HealthyPlugin.class.getName());
        assertDoesNotThrow(() -> gateway.enable(module("load-fail", healthyJar)));
        assertTrue(gateway.enabled("load-fail"));
        gateway.unload("load-fail");
    }

    @Test
    void runtimeAssetsSnapshotReflectsRuntimeContributions() throws IOException {
        Path jar = writePluginJar("assets-plugin.jar", "assets-plugin", HealthyPlugin.class.getName());
        JarPluginRuntimeGateway gateway = newGateway();
        PluginModule module = module("assets-plugin", jar);

        assertFalse(gateway.runtimeAssets("assets-plugin").loaded());

        gateway.enable(module);
        PluginRuntimeAssets assets = gateway.runtimeAssets("assets-plugin");
        assertTrue(assets.loaded());
        assertTrue(assets.enabled());
        assertEquals(1, assets.commands().size());
        assertEquals("healthy-cmd", assets.commands().get(0).code());
        assertEquals("assets-plugin", assets.commands().get(0).pluginCode());

        gateway.disable("assets-plugin");
        PluginRuntimeAssets disabled = gateway.runtimeAssets("assets-plugin");
        assertTrue(disabled.loaded());
        assertFalse(disabled.enabled());
        assertTrue(disabled.commands().isEmpty());
        gateway.unload("assets-plugin");
    }

    @Test
    void lifecycleEventsArePublishedWithOutcomeOnTransitions() throws IOException {
        Path jar = writePluginJar("events-plugin.jar", "events-plugin", HealthyPlugin.class.getName());
        List<PluginLifecycleEvent> events = new ArrayList<>();
        JarPluginRuntimeGateway gateway = newGateway(event -> {
            if (event instanceof PluginLifecycleEvent lifecycleEvent) {
                events.add(lifecycleEvent);
            }
        });
        PluginModule module = module("events-plugin", jar);

        gateway.enable(module);
        gateway.disable("events-plugin");
        gateway.unload("events-plugin");

        assertEquals(List.of(
                        PluginLifecycleAction.LOAD,
                        PluginLifecycleAction.ENABLE,
                        PluginLifecycleAction.DISABLE,
                        PluginLifecycleAction.UNLOAD),
                events.stream().map(PluginLifecycleEvent::action).toList());
        assertTrue(events.stream().allMatch(PluginLifecycleEvent::success));
        assertTrue(events.stream().allMatch(event -> "events-plugin".equals(event.pluginCode())));
    }

    public static class HealthyPlugin implements YuDreamPlugin {
        @PluginCommand(code = "healthy-cmd", command = "healthy", name = "健康指令")
        public void handle(PluginCommandContext context) {
        }
    }

    public static class ErrorOnEnablePlugin implements YuDreamPlugin {
        @PluginCommand(code = "error-cmd", command = "error", name = "失败指令")
        public void handle(PluginCommandContext context) {
        }

        @Override
        public void onEnable(PluginContext context) {
            throw new ZipError("invalid LOC header (bad signature)");
        }
    }

    public static class FailingOnEnablePlugin implements YuDreamPlugin {
        @PluginCommand(code = "failing-cmd", command = "failing", name = "失败指令")
        public void handle(PluginCommandContext context) {
        }

        @Override
        public void onEnable(PluginContext context) {
            throw new BizException("模拟启用失败");
        }
    }

    public static class FailingOnLoadPlugin implements YuDreamPlugin {
        @Override
        public void onLoad(PluginContext context) {
            throw new BizException("模拟加载失败");
        }
    }

    private JarPluginRuntimeGateway newGateway() {
        return newGateway(event -> {
        });
    }

    private JarPluginRuntimeGateway newGateway(ApplicationEventPublisher eventPublisher) {
        PluginProperties properties = new PluginProperties();
        properties.setDirectories(List.of(pluginDir.toString()));
        return new JarPluginRuntimeGateway(
                properties,
                nullReturningProxy(FrameworkServices.class),
                new PluginServiceRegistry(),
                new PluginAiToolRegistry(),
                nullReturningProxy(PluginSemanticMemoryService.class),
                new AgentRuntimeApplicationRegistry() {
                    @Override
                    public AutoCloseable register(String ownerCode, AgentApplication application) {
                        return () -> {
                        };
                    }

                    @Override
                    public Optional<AgentApplication> findByCode(String code) {
                        return Optional.empty();
                    }

                    @Override
                    public List<AgentApplication> applications() {
                        return List.of();
                    }
                },
                eventPublisher
        );
    }

    private PluginModule module(String code, Path jar) {
        return PluginModule.builder().code(code).jarPath(jar.toString()).build();
    }

    private Path writePluginJar(String fileName, String pluginCode, String mainClass) throws IOException {
        Path jar = pluginDir.resolve(fileName);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("plugin.yml"));
            String yaml = "name: " + pluginCode + "\nversion: 1.0.0\nmain: " + mainClass + "\n";
            output.write(yaml.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }

    @SuppressWarnings("unchecked")
    private static <T> T nullReturningProxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == double.class) {
                return 0d;
            }
            return null;
        });
    }
}
