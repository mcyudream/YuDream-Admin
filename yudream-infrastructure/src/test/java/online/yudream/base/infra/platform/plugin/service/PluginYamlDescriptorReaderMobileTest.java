package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.plugin.spi.core.PluginDescriptor;
import online.yudream.base.plugin.spi.core.PluginMobileSupport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginYamlDescriptorReaderMobileTest {

    private final PluginYamlDescriptorReader reader = new PluginYamlDescriptorReader();

    @Test
    void absentMobileBlockMeansUndeclared() {
        PluginDescriptor descriptor = read("""
                name: demo
                main: com.example.DemoPlugin
                version: 1.0.0
                """);
        assertNull(descriptor.mobileSupport());
    }

    @Test
    void mobileBlockDefaultsApply() {
        PluginDescriptor descriptor = read("""
                name: demo
                main: com.example.DemoPlugin
                version: 1.0.0
                mobile:
                  requiredNativeCapabilities:
                    - camera
                """);
        PluginMobileSupport mobile = descriptor.mobileSupport();
        assertEquals(List.of("android", "ios"), mobile.platforms());
        assertEquals("1.0.0", mobile.minHostVersion());
        assertEquals(List.of("camera"), mobile.requiredNativeCapabilities());
    }

    @Test
    void mobileBlockAcceptsExplicitValues() {
        PluginDescriptor descriptor = read("""
                name: demo
                main: com.example.DemoPlugin
                version: 1.0.0
                mobile:
                  platforms:
                    - ios
                  minHostVersion: 2.3.0
                  requiredNativeCapabilities:
                    - biometric
                    - push
                """);
        assertEquals(new PluginMobileSupport(List.of("ios"), "2.3.0", List.of("biometric", "push")),
                descriptor.mobileSupport());
    }

    @Test
    void mobilePlatformsRejectIllegalValue() {
        BizException exception = assertThrows(BizException.class, () -> read("""
                name: demo
                main: com.example.DemoPlugin
                version: 1.0.0
                mobile:
                  platforms:
                    - harmonyos
                """));
        assertTrue(exception.getMessage().contains("android/ios"));
    }

    @Test
    void mobileBlockRejectsNonObjectAndIllegalVersion() {
        assertThrows(BizException.class, () -> read("""
                name: demo
                main: com.example.DemoPlugin
                version: 1.0.0
                mobile: true
                """));
        assertThrows(BizException.class, () -> read("""
                name: demo
                main: com.example.DemoPlugin
                version: 1.0.0
                mobile:
                  minHostVersion: latest
                """));
    }

    private PluginDescriptor read(String yaml) {
        return reader.read(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
