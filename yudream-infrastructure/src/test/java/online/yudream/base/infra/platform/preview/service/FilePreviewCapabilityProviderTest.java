package online.yudream.base.infra.platform.preview.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityStatus;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import online.yudream.base.plugin.spi.system.preview.PluginFilePreviewService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilePreviewCapabilityProviderTest {

    private static FilePreviewCapabilityProvider provider() {
        return new FilePreviewCapabilityProvider(new StandardEnvironment());
    }

    @Test
    void descriptorIsStable() {
        CapabilityDescriptor descriptor = provider().descriptor();
        assertEquals("file-preview", descriptor.code());
        assertEquals(CapabilityType.DOCUMENT, descriptor.type());
        assertFalse(descriptor.defaultConfig().isEmpty());
        assertTrue(descriptor.defaultConfig().containsKey(PluginFilePreviewService.CONFIG_BASE_URL));
        assertEquals(PluginFilePreviewService.DEFAULT_OFFICE_PREVIEW_TYPE,
                descriptor.defaultConfig().get(PluginFilePreviewService.CONFIG_OFFICE_PREVIEW_TYPE));
    }

    @Test
    void disabledByDefault() {
        FilePreviewCapabilityProvider provider = provider();
        assertFalse(provider.active());
        assertEquals(CapabilityStatus.DISABLED, provider.health().status());
        CapabilityTestResult result = provider.test(null);
        assertFalse(result.success());
    }

    @Test
    void enableRejectsBlankBaseUrl() {
        FilePreviewCapabilityProvider provider = provider();
        assertThrows(BizException.class, () -> provider.enable(Map.of(
                PluginFilePreviewService.CONFIG_BASE_URL, "  ")));
        assertFalse(provider.active());
    }

    @Test
    void enableRejectsNonHttpBaseUrl() {
        FilePreviewCapabilityProvider provider = provider();
        assertThrows(BizException.class, () -> provider.enable(Map.of(
                PluginFilePreviewService.CONFIG_BASE_URL, "ftp://kk:8012")));
        assertThrows(BizException.class, () -> provider.enable(Map.of(
                PluginFilePreviewService.CONFIG_BASE_URL, "kk:8012")));
    }

    @Test
    void enableRejectsNonHttpCallbackBaseUrl() {
        FilePreviewCapabilityProvider provider = provider();
        assertThrows(BizException.class, () -> provider.enable(Map.of(
                PluginFilePreviewService.CONFIG_BASE_URL, "http://kk:8012",
                PluginFilePreviewService.CONFIG_CALLBACK_BASE_URL, "backend:8080")));
    }

    @Test
    void enableMergesDefaultsAndActivates() {
        FilePreviewCapabilityProvider provider = provider();
        provider.enable(Map.of(PluginFilePreviewService.CONFIG_BASE_URL, " http://kk:8012/ "));
        assertTrue(provider.active());
        assertEquals("http://kk:8012/", provider.configValue(PluginFilePreviewService.CONFIG_BASE_URL));
        // 未提供的键回退默认配置
        assertEquals(PluginFilePreviewService.DEFAULT_OFFICE_PREVIEW_TYPE,
                provider.configValue(PluginFilePreviewService.CONFIG_OFFICE_PREVIEW_TYPE));
        assertEquals(String.valueOf(PluginFilePreviewService.DEFAULT_TOKEN_TTL_SECONDS),
                provider.configValue(PluginFilePreviewService.CONFIG_TOKEN_TTL_SECONDS));
        CapabilityHealth health = provider.health();
        assertEquals(CapabilityStatus.ENABLED, health.status());
    }

    @Test
    void disableDeactivates() {
        FilePreviewCapabilityProvider provider = provider();
        provider.enable(Map.of(PluginFilePreviewService.CONFIG_BASE_URL, "http://kk:8012"));
        provider.disable();
        assertFalse(provider.active());
        assertEquals(CapabilityStatus.DISABLED, provider.health().status());
    }

    @Test
    void testProbesBaseUrlAndReportsUnreachable() {
        FilePreviewCapabilityProvider provider = provider();
        provider.enable(Map.of(PluginFilePreviewService.CONFIG_BASE_URL, "http://127.0.0.1:1"));
        // 内网兜底地址未配置，直连 127.0.0.1:1 必失败
        CapabilityTestResult result = provider.test(null);
        assertFalse(result.success());
    }
}
