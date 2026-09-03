package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.infra.platform.preview.service.FilePreviewCapabilityProvider;
import online.yudream.base.plugin.spi.system.preview.PluginFilePreviewService;
import online.yudream.base.plugin.spi.system.preview.PluginPreviewFile;
import online.yudream.base.plugin.spi.system.preview.PluginPreviewInfo;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginFilePreviewFrameworkServiceTest {

    /** 手搓桩：能力 Provider 由测试直接装配，签名密钥固定。 */
    private static class StubService extends PluginFilePreviewFrameworkService {
        private FilePreviewCapabilityProvider capability;

        StubService() {
            super(null, null, null);
        }

        /** 部署侧闸门关闭：Provider Bean 不存在。 */
        StubService withoutCapability() {
            capability = null;
            return this;
        }

        /** 装配并启用能力（运行侧闸门打开）。 */
        StubService withCapability(Map<String, String> config) {
            capability = new FilePreviewCapabilityProvider(new StandardEnvironment());
            capability.enable(config);
            return this;
        }

        /** 装配能力但不启用（运行侧闸门关闭）。 */
        StubService withDisabledCapability() {
            capability = new FilePreviewCapabilityProvider(new StandardEnvironment());
            return this;
        }

        @Override
        protected FilePreviewCapabilityProvider capability() {
            return capability;
        }

        @Override
        protected String credentialKey() {
            return "test-credential-key";
        }
    }

    private static PluginPreviewFile file(String objectKey, String filename, long size) {
        return new PluginPreviewFile(objectKey, filename, "application/octet-stream", size);
    }

    @Test
    void signAndVerifyRoundTrip() {
        StubService service = new StubService();
        String token = service.sign("material", "material/2026/a.png", 1800);
        Optional<PluginFilePreviewFrameworkService.TokenPayload> payload = service.verify(token);
        assertTrue(payload.isPresent());
        assertEquals("material", payload.get().pluginCode());
        assertEquals("material/2026/a.png", payload.get().objectKey());
    }

    @Test
    void verifyRejectsTamperedAndMalformedTokens() {
        StubService service = new StubService();
        String token = service.sign("material", "material/2026/a.png", 1800);
        assertTrue(service.verify(token.substring(0, token.length() - 1) + "0").isEmpty());
        assertTrue(service.verify("not-a-token").isEmpty());
        assertTrue(service.verify("").isEmpty());
        // 其他密钥签发的 token 不可通过
        StubService other = new StubService() {
            @Override
            protected String credentialKey() {
                return "another-key";
            }
        };
        assertTrue(other.verify(token).isEmpty());
    }

    @Test
    void verifyRejectsExpiredToken() throws Exception {
        StubService service = new StubService();
        String payload = "material|material/2026/a.png|1";
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                "plugin-file-preview:test-credential-key".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertTrue(service.verify(encoded + "." + signature).isEmpty());
    }

    @Test
    void signedFileUrlValidatesInputs() {
        StubService service = new StubService();
        assertEquals("", service.signedFileUrl("bad code!", "k/a.png", "a.png"));
        assertEquals("", service.signedFileUrl("material", "../escape", "a.png"));
        assertEquals("", service.signedFileUrl("material", "k//a.png", "a.png"));
        assertEquals("", service.signedFileUrl("material", "", "a.png"));
        assertEquals("", service.signedFileUrl("material", "k|a.png", "a.png"));
        String url = service.signedFileUrl("material", "k/a.png", "a.png");
        assertTrue(url.startsWith(PluginFilePreviewFrameworkService.PUBLIC_FILE_PATH + "/"), url);
        assertTrue(url.endsWith("/a.png"), url);
    }

    @Test
    void signedFileUrlNormalizesObjectKeyAndEncodesFilename() {
        StubService service = new StubService();
        String url = service.signedFileUrl("material", "/material/a b.png", "a b.png");
        assertTrue(url.endsWith("/a%20b.png"), url);
        String token = url.substring(PluginFilePreviewFrameworkService.PUBLIC_FILE_PATH.length() + 1, url.lastIndexOf('/'));
        Optional<PluginFilePreviewFrameworkService.TokenPayload> payload = service.verify(token);
        assertTrue(payload.isPresent());
        assertEquals("material/a b.png", payload.get().objectKey());
    }

    @Test
    void capabilityAbsentOrDisabledFallsBackToDirectOrNone() {
        // 部署侧闸门关闭（无 Provider Bean）：图片 DIRECT、docx NONE
        StubService absent = new StubService().withoutCapability();
        assertFalse(absent.enabled());
        assertEquals(PluginPreviewInfo.MODE_DIRECT, absent.preview("material", file("material/a.png", "a.png", 100)).mode());
        assertEquals(PluginPreviewInfo.MODE_NONE, absent.preview("material", file("material/a.docx", "a.docx", 100)).mode());
        // 运行侧闸门关闭（Provider 存在但未启用）：同样回退
        StubService disabled = new StubService().withDisabledCapability();
        assertFalse(disabled.enabled());
        PluginPreviewInfo image = disabled.preview("material", file("material/a.png", "a.png", 100));
        assertEquals(PluginPreviewInfo.MODE_DIRECT, image.mode());
        assertTrue(image.url().startsWith(PluginFilePreviewFrameworkService.PUBLIC_FILE_PATH + "/"));
        assertEquals(PluginPreviewInfo.MODE_NONE, disabled.preview("material", file("material/a.docx", "a.docx", 100)).mode());
    }

    @Test
    void enabledCapabilityHonoursSizeLimit() {
        StubService limited = new StubService().withCapability(Map.of(
                PluginFilePreviewService.CONFIG_BASE_URL, "http://kk:8012",
                PluginFilePreviewService.CONFIG_MAX_PREVIEW_SIZE_MB, "1"));
        PluginPreviewInfo oversize = limited.preview("material",
                file("material/a.png", "a.png", 2L * 1024 * 1024));
        assertEquals(PluginPreviewInfo.MODE_NONE, oversize.mode());
    }

    @Test
    void kkReadyProducesKkfileUrl() {
        StubService service = new StubService().withCapability(Map.of(
                PluginFilePreviewService.CONFIG_BASE_URL, "http://kk:8012/",
                PluginFilePreviewService.CONFIG_CALLBACK_BASE_URL, "https://admin.example.com",
                PluginFilePreviewService.CONFIG_OFFICE_PREVIEW_TYPE, "pdf"));
        assertTrue(service.enabled());
        PluginPreviewInfo info = service.preview("material", file("material/a.docx", "a.docx", 100));
        assertEquals(PluginPreviewInfo.MODE_KKFILE, info.mode());
        assertTrue(info.url().startsWith("http://kk:8012/onlinePreview?url="), info.url());
        assertTrue(info.url().contains("&officePreviewType=pdf"), info.url());
        // 回源地址进入 base64 负载
        String encoded = info.url().substring("http://kk:8012/onlinePreview?url=".length(), info.url().indexOf('&'));
        String fileUrl = new String(java.util.Base64.getDecoder().decode(
                java.net.URLDecoder.decode(encoded, java.nio.charset.StandardCharsets.UTF_8)),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(fileUrl.startsWith("https://admin.example.com" + PluginFilePreviewFrameworkService.PUBLIC_FILE_PATH + "/"), fileUrl);
    }

    @Test
    void browserRenderablePrefersDirectEvenWhenKkReady() {
        StubService service = new StubService().withCapability(Map.of(
                PluginFilePreviewService.CONFIG_BASE_URL, "http://kk:8012",
                PluginFilePreviewService.CONFIG_CALLBACK_BASE_URL, "https://admin.example.com"));
        PluginPreviewInfo image = service.preview("material", file("material/a.png", "a.png", 100));
        assertEquals(PluginPreviewInfo.MODE_DIRECT, image.mode());
        assertTrue(image.url().startsWith(PluginFilePreviewFrameworkService.PUBLIC_FILE_PATH + "/"), image.url());
        // 外部地址同样直读优先
        PluginPreviewInfo external = service.previewExternal("https://cdn.example.com/x/a.pdf", "pdf", 100);
        assertEquals(PluginPreviewInfo.MODE_DIRECT, external.mode());
        assertEquals("https://cdn.example.com/x/a.pdf", external.url());
    }

    @Test
    void previewExternalKeepsAbsoluteUrlForDirect() {
        StubService service = new StubService();
        PluginPreviewInfo info = service.previewExternal("https://cdn.example.com/x/a.png", "png", 100);
        assertEquals(PluginPreviewInfo.MODE_DIRECT, info.mode());
        assertEquals("https://cdn.example.com/x/a.png", info.url());
    }

    @Test
    void browserRenderableMatrix() {
        assertTrue(PluginFilePreviewFrameworkService.browserRenderable("PNG"));
        assertTrue(PluginFilePreviewFrameworkService.browserRenderable("mp3"));
        assertTrue(PluginFilePreviewFrameworkService.browserRenderable("mp4"));
        assertTrue(PluginFilePreviewFrameworkService.browserRenderable("pdf"));
        assertTrue(PluginFilePreviewFrameworkService.browserRenderable("md"));
        assertFalse(PluginFilePreviewFrameworkService.browserRenderable("docx"));
        assertFalse(PluginFilePreviewFrameworkService.browserRenderable("psd"));
        assertFalse(PluginFilePreviewFrameworkService.browserRenderable(""));
    }

    @Test
    void invalidConfigValuesFallBackToDefaults() {
        StubService service = new StubService().withCapability(Map.of(
                PluginFilePreviewService.CONFIG_BASE_URL, "http://kk:8012",
                PluginFilePreviewService.CONFIG_TOKEN_TTL_SECONDS, "not-a-number",
                PluginFilePreviewService.CONFIG_MAX_PREVIEW_SIZE_MB, "99999"));
        assertEquals(PluginFilePreviewService.DEFAULT_TOKEN_TTL_SECONDS, service.tokenTtlSeconds());
        assertEquals(PluginFilePreviewService.DEFAULT_MAX_PREVIEW_SIZE_MB, service.maxPreviewSizeMb());
    }
}
