package online.yudream.base.infra.platform.plugin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PluginClassLoaderIntegrationTest {

    @Test
    @EnabledIfSystemProperty(named = "yudream.plugin.classloader.it", matches = "true")
    void resolvesProviderApiFromDeclaredDependencyJar() throws Exception {
        Path workspaceRoot = Path.of(System.getProperty("user.dir")).getParent();
        Path walletJar = workspaceRoot.resolve("plugins/yudream-plugin-wallet-1.0-SNAPSHOT.jar");
        Path alipayJar = workspaceRoot.resolve("plugins/yudream-plugin-alipay-1.0-SNAPSHOT.jar");
        assertThat(Files.isRegularFile(walletJar)).isTrue();
        assertThat(Files.isRegularFile(alipayJar)).isTrue();

        try (PluginClassLoader walletLoader = new PluginClassLoader(
                new URL[]{walletJar.toUri().toURL()}, getClass().getClassLoader(), List.of());
             PluginClassLoader alipayLoader = new PluginClassLoader(
                     new URL[]{alipayJar.toUri().toURL()}, getClass().getClassLoader(), List.of(walletLoader))) {
            Class<?> walletApi = alipayLoader.loadClass("online.yudream.base.plugin.wallet.api.PluginWalletService");
            Class<?> providerApi = walletLoader.loadClass("online.yudream.base.plugin.wallet.api.PluginWalletService");

            assertThat(walletApi).isSameAs(providerApi);
        }
    }
}
