package online.yudream.base.domain.platform.mobile.valobj;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mobile.enumerate.MobilePlatform;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobilePluginSupportTest {

    @Test
    void undeclaredBlockNeverEntersManifest() {
        MobilePluginSupport support = MobilePluginSupport.undeclared();
        assertFalse(support.declared());
        assertFalse(support.supports(MobilePlatform.ANDROID));
        assertFalse(support.supports(MobilePlatform.IOS));
        assertFalse(support.availableFor(MobilePlatform.ANDROID, Set.of(), "9.9.9"));
    }

    @Test
    void declaredBlockAppliesDocumentedDefaults() {
        MobilePluginSupport support = MobilePluginSupport.declared(List.of(), null, List.of(" "));
        assertTrue(support.declared());
        assertTrue(support.supports(MobilePlatform.ANDROID));
        assertTrue(support.supports(MobilePlatform.IOS));
        assertEquals("1.0.0", support.minHostVersion());
        assertTrue(support.requiredNativeCapabilities().isEmpty());
        assertEquals(List.of("android", "ios"), support.platformTokens());
    }

    @Test
    void platformFilterMatchesDeclaredPlatformOnly() {
        MobilePluginSupport androidOnly = MobilePluginSupport.declared(List.of(MobilePlatform.ANDROID), null, List.of());
        assertTrue(androidOnly.supports(MobilePlatform.ANDROID));
        assertFalse(androidOnly.supports(MobilePlatform.IOS));
        // manifest 过滤：ios 请求在 android-only 插件上被过滤
        assertFalse(androidOnly.availableFor(MobilePlatform.IOS, Set.of(), "1.0.0"));
    }

    @Test
    void iosScenarioPassesAllThreeGates() {
        // 协议层 iOS 席位：插件声明 android+ios，要求 camera+push；iOS 宿主上报满足版本与能力
        MobilePluginSupport support = MobilePluginSupport.declared(
                List.of(MobilePlatform.ANDROID, MobilePlatform.IOS), "2.1.0", List.of("camera", "push"));
        assertTrue(support.availableFor(MobilePlatform.IOS, Set.of("camera", "push", "extra"), "2.1.0"));
        assertTrue(support.availableFor(MobilePlatform.IOS, Set.of("camera", "push"), "3.0.0"));
        // iOS 缺能力 → 过滤
        assertFalse(support.availableFor(MobilePlatform.IOS, Set.of("camera"), "3.0.0"));
        // iOS 宿主版本过低 → 过滤
        assertFalse(support.availableFor(MobilePlatform.IOS, Set.of("camera", "push"), "2.0.9"));
        // 同一插件 android 席位不受 iOS 判定影响
        assertTrue(support.availableFor(MobilePlatform.ANDROID, Set.of("camera", "push"), "2.1.0"));
    }

    @Test
    void capabilitySetEmptyFailsOnlyWhenPluginRequiresCapabilities() {
        MobilePluginSupport requiring = MobilePluginSupport.declared(null, null, List.of("biometric"));
        assertFalse(requiring.satisfiesNativeCapabilities(Set.of()));
        assertFalse(requiring.satisfiesNativeCapabilities(null));
        MobilePluginSupport plain = MobilePluginSupport.declared(null, null, List.of());
        assertTrue(plain.satisfiesNativeCapabilities(Set.of()));
        assertTrue(plain.satisfiesNativeCapabilities(null));
    }

    @Test
    void registrationRejectsIllegalPlatformToken() {
        assertThrows(BizException.class, () -> MobilePluginSupport.requireLegalPlatformToken("harmonyos"));
        assertThrows(BizException.class, () -> MobilePluginSupport.requireLegalPlatformToken(""));
        assertEquals(MobilePlatform.IOS, MobilePluginSupport.requireLegalPlatformToken("ios"));
        assertEquals(MobilePlatform.ANDROID, MobilePluginSupport.requireLegalPlatformToken("Android"));
    }

    @Test
    void capabilityTokensNormalizeToLowercase() {
        MobilePluginSupport support = MobilePluginSupport.declared(null, null, List.of("Camera", " PUSH "));
        assertEquals(List.of("camera", "push"), support.normalizedCapabilityTokens());
    }
}
