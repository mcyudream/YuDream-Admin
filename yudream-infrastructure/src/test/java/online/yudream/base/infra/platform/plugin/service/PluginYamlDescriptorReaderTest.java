package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.plugin.spi.core.PluginDescriptor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginYamlDescriptorReaderTest {

    @Test
    void readsOptionalGitUrl() {
        PluginDescriptor descriptor = reader().read(stream("""
                name: demo
                version: 1.0.0
                main: example.Plugin
                git: https://github.com/yudream/demo-plugin
                """));
        assertEquals("https://github.com/yudream/demo-plugin", descriptor.gitUrl());
    }

    @Test
    void gitUrlAbsentStaysNull() {
        PluginDescriptor descriptor = reader().read(stream("""
                name: demo
                version: 1.0.0
                main: example.Plugin
                """));
        assertNull(descriptor.gitUrl());
    }

    @Test
    void rejectsNonHttpGitUrl() {
        PluginYamlDescriptorReader reader = reader();
        assertThrows(BizException.class, () -> reader.read(stream("""
                name: demo
                version: 1.0.0
                main: example.Plugin
                git: git@github.com:yudream/demo.git
                """)));
    }

    private PluginYamlDescriptorReader reader() {
        return new PluginYamlDescriptorReader();
    }

    private java.io.InputStream stream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
