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
    void readsSystemIconAndImagePathFromPluginYaml() {
        PluginYamlDescriptorReader reader = new PluginYamlDescriptorReader();

        PluginDescriptor systemIcon = reader.read(new ByteArrayInputStream("""
                name: system-icon
                main: example.Plugin
                version: 1.0.0
                icon: i-ri:puzzle-2-line
                """.getBytes(StandardCharsets.UTF_8)));
        PluginDescriptor imageIcon = reader.read(new ByteArrayInputStream("""
                name: image-icon
                main: example.Plugin
                version: 1.0.0
                icon: assets/icon.png
                """.getBytes(StandardCharsets.UTF_8)));

        assertThat(systemIcon.icon()).isEqualTo("i-ri:puzzle-2-line");
        assertThat(imageIcon.icon()).isEqualTo("assets/icon.png");
    }

    @Test
    void rejectsUnsafePluginIconPath() {
        org.junit.jupiter.api.Assertions.assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                () -> new PluginYamlDescriptorReader().read(new ByteArrayInputStream("""
                        name: unsafe-icon
                        main: example.Plugin
                        version: 1.0.0
                        icon: ../icon.png
                        """.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void fallsBackToCodeWhenDisplayNameMissing() {
        PluginDescriptor descriptor = new PluginYamlDescriptorReader().read(new ByteArrayInputStream("""
                name: legacy-plugin
                main: online.yudream.plugin.legacy.LegacyPlugin
                version: 1.0.0
                """.getBytes(StandardCharsets.UTF_8)));

        assertThat(descriptor.code()).isEqualTo("legacy-plugin");
        assertThat(descriptor.name()).isEqualTo("legacy-plugin");
    }
}
