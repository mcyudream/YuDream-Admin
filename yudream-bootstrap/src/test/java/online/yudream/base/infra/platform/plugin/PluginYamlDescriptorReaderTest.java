package online.yudream.base.infra.platform.plugin;

import online.yudream.base.infra.platform.plugin.service.PluginYamlDescriptorReader;
import online.yudream.base.plugin.spi.core.PluginDescriptor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PluginYamlDescriptorReaderTest {

    @Test
    void readsHardAndSoftDependenciesFromPluginYaml() {
        PluginDescriptor descriptor = new PluginYamlDescriptorReader().read(new ByteArrayInputStream("""
                name: order-plugin
                displayName: 订单插件
                main: online.yudream.plugin.order.OrderPlugin
                version: 1.2.3
                depend:
                  - wallet-plugin
                softdepend:
                  - coupon-plugin
                """.getBytes(StandardCharsets.UTF_8)));

        assertThat(descriptor.code()).isEqualTo("order-plugin");
        assertThat(descriptor.name()).isEqualTo("订单插件");
        assertThat(descriptor.mainClass()).isEqualTo("online.yudream.plugin.order.OrderPlugin");
        assertThat(descriptor.dependencies()).isEqualTo(List.of("wallet-plugin"));
        assertThat(descriptor.softDependencies()).isEqualTo(List.of("coupon-plugin"));
    }

    @Test
    void fallsBackToPluginCodeWhenDisplayNameIsMissing() {
        PluginDescriptor descriptor = new PluginYamlDescriptorReader().read(new ByteArrayInputStream("""
                name: legacy-plugin
                main: online.yudream.plugin.legacy.LegacyPlugin
                version: 1.0.0
                """.getBytes(StandardCharsets.UTF_8)));

        assertThat(descriptor.code()).isEqualTo("legacy-plugin");
        assertThat(descriptor.name()).isEqualTo("legacy-plugin");
    }
}
