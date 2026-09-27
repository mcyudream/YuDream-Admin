package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.plugin.spi.core.PluginDescriptor;
import online.yudream.base.plugin.spi.core.PluginMobileHomeCard;
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
        assertNull(mobile.name());
        assertNull(mobile.description());
        assertNull(mobile.icon());
        assertTrue(mobile.homeCards().isEmpty());
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
        assertEquals(new PluginMobileSupport(List.of("ios"), "2.3.0", List.of("biometric", "push"),
                        null, null, null, null),
                descriptor.mobileSupport());
    }

    @Test
    void mobileDisplayAndHomeCardsParseFully() {
        PluginDescriptor descriptor = read("""
                name: demo
                main: com.example.DemoPlugin
                version: 1.0.0
                mobile:
                  platforms: [android, ios]
                  minHostVersion: 0.1.0
                  name: 论坛
                  description: 社区讨论
                  icon: chatbubbles-outline
                  home:
                    cards:
                      - id: latest-posts
                        title: 最新帖子
                        description: 社区最新动态
                        icon: flame-outline
                        route: /posts/latest
                      - id: hot-posts
                        title: 热门帖子
                        route: /posts/hot
                """);
        PluginMobileSupport mobile = descriptor.mobileSupport();
        assertEquals("论坛", mobile.name());
        assertEquals("社区讨论", mobile.description());
        assertEquals("chatbubbles-outline", mobile.icon());
        assertEquals(2, mobile.homeCards().size());
        assertEquals(new PluginMobileHomeCard("latest-posts", "最新帖子", "社区最新动态", "flame-outline", "/posts/latest"),
                mobile.homeCards().get(0));
        // 可选字段缺省保持 null
        assertEquals(new PluginMobileHomeCard("hot-posts", "热门帖子", null, null, "/posts/hot"),
                mobile.homeCards().get(1));
    }

    @Test
    void mobileDisplayFieldsParseWithoutHomeBlock() {
        PluginDescriptor descriptor = read("""
                name: demo
                main: com.example.DemoPlugin
                version: 1.0.0
                mobile:
                  name: 论坛
                  description: 社区讨论
                  icon: chatbubbles-outline
                """);
        PluginMobileSupport mobile = descriptor.mobileSupport();
        assertEquals("论坛", mobile.name());
        assertEquals("社区讨论", mobile.description());
        assertEquals("chatbubbles-outline", mobile.icon());
        assertTrue(mobile.homeCards().isEmpty());
    }

    @Test
    void mobileDisplayFieldsRejectOverlongValues() {
        assertThrows(BizException.class, () -> read(yamlWithMobile("name: " + "n".repeat(65))));
        assertThrows(BizException.class, () -> read(yamlWithMobile("description: " + "d".repeat(257))));
        assertThrows(BizException.class, () -> read(yamlWithMobile("icon: " + "i".repeat(65))));
    }

    @Test
    void mobileHomeCardsRejectIllegalShapes() {
        // home 不是对象
        assertThrows(BizException.class, () -> read(yamlWithMobile("home: true")));
        // cards 不是列表
        assertThrows(BizException.class, () -> read(yamlWithMobile("home:\n    cards: latest-posts")));
        // 卡片不是对象
        assertThrows(BizException.class, () -> read(yamlWithMobile("home:\n    cards:\n      - latest-posts")));
    }

    @Test
    void mobileHomeCardsRejectOverTenEntries() {
        StringBuilder cards = new StringBuilder();
        for (int i = 1; i <= 11; i++) {
            cards.append("      - {id: card-").append(i).append(", title: 卡片").append(i)
                    .append(", route: /posts/").append(i).append("}\n");
        }
        BizException exception = assertThrows(BizException.class,
                () -> read(yamlWithMobile("home:\n    cards:\n" + cards)));
        assertTrue(exception.getMessage().contains("数量不能超过 10"));
    }

    @Test
    void mobileHomeCardsRejectInvalidRequiredFields() {
        // id 缺失
        assertThrows(BizException.class, () -> read(card("{title: 最新帖子, route: /posts/latest}")));
        // id 大写字母不匹配 [a-z0-9-]
        BizException badId = assertThrows(BizException.class,
                () -> read(card("{id: LatestPosts, title: 最新帖子, route: /posts/latest}")));
        assertTrue(badId.getMessage().contains("cards[0].id 必须匹配 [a-z0-9-]"));
        // id 重复
        BizException duplicate = assertThrows(BizException.class, () -> read("""
                name: demo
                main: com.example.DemoPlugin
                version: 1.0.0
                mobile:
                  home:
                    cards:
                      - {id: latest-posts, title: 最新帖子, route: /posts/latest}
                      - {id: latest-posts, title: 热门帖子, route: /posts/hot}
                """));
        assertTrue(duplicate.getMessage().contains("cards[1].id 重复"));
        // title 缺失
        assertThrows(BizException.class, () -> read(card("{id: latest-posts, route: /posts/latest}")));
        // title 超长（33 字符）
        assertThrows(BizException.class, () -> read(cardY("id: latest-posts", "title: " + "标".repeat(33),
                "route: /posts/latest")));
        // description 超长（129 字符）
        assertThrows(BizException.class, () -> read(cardY("id: latest-posts", "title: 最新帖子",
                "description: " + "描".repeat(129), "route: /posts/latest")));
        // route 缺失
        assertThrows(BizException.class, () -> read(card("{id: latest-posts, title: 最新帖子}")));
        // route 不以 / 开头
        BizException badRoute = assertThrows(BizException.class,
                () -> read(card("{id: latest-posts, title: 最新帖子, route: posts/latest}")));
        assertTrue(badRoute.getMessage().contains("cards[0].route 必须以 / 开头"));
        // route 超长（129 字符）
        assertThrows(BizException.class, () -> read(cardY("id: latest-posts", "title: 最新帖子",
                "route: /" + "p".repeat(128))));
        // 错误文案带序号与字段名
        BizException second = assertThrows(BizException.class, () -> read("""
                name: demo
                main: com.example.DemoPlugin
                version: 1.0.0
                mobile:
                  home:
                    cards:
                      - {id: latest-posts, title: 最新帖子, route: /posts/latest}
                      - {id: hot-posts, title: 热门帖子, route: posts/hot}
                """));
        assertTrue(second.getMessage().contains("mobile.home.cards[1].route"));
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

    /** 单卡片的完整 plugin.yml：cardYaml 为 flow 风格 map 文本。 */
    private static String card(String cardYaml) {
        return yamlWithMobile("home:\n    cards:\n      - " + cardYaml);
    }

    private static String cardY(String... lines) {
        return yamlWithMobile("home:\n    cards:\n      - {" + String.join(", ", lines) + "}");
    }

    private static String yamlWithMobile(String mobileBody) {
        return """
                name: demo
                main: com.example.DemoPlugin
                version: 1.0.0
                mobile:
                  %s
                """.formatted(mobileBody);
    }

    private PluginDescriptor read(String yaml) {
        return reader.read(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
