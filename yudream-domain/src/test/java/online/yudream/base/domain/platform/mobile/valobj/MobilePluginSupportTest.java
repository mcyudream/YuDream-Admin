package online.yudream.base.domain.platform.mobile.valobj;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mobile.enumerate.MobilePlatform;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        // 未声明块：展示字段为 null、主页卡片为空列表、信息流内容源为 null
        assertNull(support.name());
        assertEquals(List.of(), support.homeCards());
        assertNull(support.homeFeed());
    }

    @Test
    void declaredBlockAppliesDocumentedDefaults() {
        MobilePluginSupport support = MobilePluginSupport.declared(List.of(), null, List.of(" "),
                null, null, null, null, null, null);
        assertTrue(support.declared());
        assertTrue(support.supports(MobilePlatform.ANDROID));
        assertTrue(support.supports(MobilePlatform.IOS));
        assertEquals("1.0.0", support.minHostVersion());
        assertTrue(support.requiredNativeCapabilities().isEmpty());
        assertEquals(List.of("android", "ios"), support.platformTokens());
        // 展示与主页卡片字段缺省回落空串/空列表
        assertEquals("", support.name());
        assertEquals("", support.description());
        assertEquals("", support.icon());
        assertEquals(List.of(), support.homeCards());
    }

    @Test
    void platformFilterMatchesDeclaredPlatformOnly() {
        MobilePluginSupport androidOnly = MobilePluginSupport.declared(List.of(MobilePlatform.ANDROID), null, List.of(),
                null, null, null, null, null, null);
        assertTrue(androidOnly.supports(MobilePlatform.ANDROID));
        assertFalse(androidOnly.supports(MobilePlatform.IOS));
        // manifest 过滤：ios 请求在 android-only 插件上被过滤
        assertFalse(androidOnly.availableFor(MobilePlatform.IOS, Set.of(), "1.0.0"));
    }

    @Test
    void iosScenarioPassesAllThreeGates() {
        // 协议层 iOS 席位：插件声明 android+ios，要求 camera+push；iOS 宿主上报满足版本与能力
        MobilePluginSupport support = MobilePluginSupport.declared(
                List.of(MobilePlatform.ANDROID, MobilePlatform.IOS), "2.1.0", List.of("camera", "push"),
                null, null, null, null, null, null);
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
        MobilePluginSupport requiring = MobilePluginSupport.declared(null, null, List.of("biometric"),
                null, null, null, null, null, null);
        assertFalse(requiring.satisfiesNativeCapabilities(Set.of()));
        assertFalse(requiring.satisfiesNativeCapabilities(null));
        MobilePluginSupport plain = MobilePluginSupport.declared(null, null, List.of(),
                null, null, null, null, null, null);
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
        MobilePluginSupport support = MobilePluginSupport.declared(null, null, List.of("Camera", " PUSH "),
                null, null, null, null, null, null);
        assertEquals(List.of("camera", "push"), support.normalizedCapabilityTokens());
    }

    @Test
    void displayAndHomeCardsPassThroughWithoutAffectingFilter() {
        List<MobileHomeCard> cards = List.of(
                new MobileHomeCard("latest-posts", "最新帖子", "社区最新动态", "flame-outline", "/posts/latest", null),
                new MobileHomeCard(null, null, null, null, null, null));
        MobilePluginSupport support = MobilePluginSupport.declared(
                List.of(MobilePlatform.ANDROID), "1.2.3", List.of(),
                "论坛", " 社区讨论 ", "chatbubbles-outline", cards, null, null);
        assertEquals("论坛", support.name());
        assertEquals("社区讨论", support.description());
        assertEquals("chatbubbles-outline", support.icon());
        assertEquals(2, support.homeCards().size());
        assertEquals("latest-posts", support.homeCards().get(0).id());
        // 展示与主页卡片不参与 manifest 过滤判定
        assertTrue(support.availableFor(MobilePlatform.ANDROID, Set.of(), "1.2.3"));
        assertFalse(support.availableFor(MobilePlatform.IOS, Set.of(), "1.2.3"));
        // null 卡片被丢弃，字段归一为空串
        assertEquals("", support.homeCards().get(1).id());
    }

    @Test
    void homeFeedPassesThroughWithoutAffectingFilter() {
        MobilePluginSupport support = MobilePluginSupport.declared(
                List.of(MobilePlatform.ANDROID, MobilePlatform.IOS), null, List.of(),
                null, null, null, null, new MobileHomeFeed("/public/mobile-feed", "论坛动态"), null);
        assertEquals("/public/mobile-feed", support.homeFeed().endpoint());
        assertEquals("论坛动态", support.homeFeed().title());
        // 信息流内容源声明不参与 manifest 过滤判定
        assertTrue(support.availableFor(MobilePlatform.IOS, Set.of(), "1.0.0"));
        assertFalse(support.availableFor(MobilePlatform.IOS, Set.of(), "0.9.9"));
        // homeFeed 为 null 的声明保持 null，等值比较含 feed 组件
        MobilePluginSupport withoutFeed = MobilePluginSupport.declared(
                List.of(MobilePlatform.ANDROID), null, List.of(),
                null, null, null, null, null, null);
        assertNull(withoutFeed.homeFeed());
        assertEquals(MobilePluginSupport.declared(List.of(MobilePlatform.ANDROID), null, List.of(),
                null, null, null, null, null, null), withoutFeed);
    }
}
