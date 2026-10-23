package online.yudream.base.application.system.dashboard.service;

import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDashboardCardInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchRequest;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpEndpointInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginPermissionInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginRuntimeAssets;
import online.yudream.base.domain.system.dashboard.valobj.DashboardCardDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardCardRegistryTest {

    @Test
    void messagingBindingCardCarriesExpectedMetadata() {
        DashboardCardDefinition card = messagingBindingCard();

        assertEquals("system.messaging-binding", card.code());
        assertEquals("消息协议绑定", card.title());
        assertEquals("按机器人连接绑定消息身份，生成绑定码并复制发送命令；官方 OpenID 不覆盖 QQ 号。", card.description());
        assertEquals("morph:bot", card.icon());
        assertEquals("个人", card.category());
        assertEquals("SYSTEM", card.source());
        assertNull(card.pluginCode());
        assertEquals("MESSAGING_BINDING", card.component());
        assertNull(card.actionPath());
        assertNull(card.dragPayloadTemplate());
        assertEquals("gray", card.tone());
        assertEquals(4, card.defaultW());
        assertEquals(3, card.defaultH());
        assertEquals(3, card.minW());
        assertEquals(3, card.minH());
        assertEquals(25, card.sort());
        assertFalse(card.defaultOnFirstVisit());
    }

    @Test
    void messagingBindingCardNeedsNoAdminPermission() {
        DashboardCardDefinition card = messagingBindingCard();

        // 个人绑定端点所有登录用户可用：permission 为空即不需要管理权限
        assertTrue(card.permission() == null || card.permission().isBlank());
        // 卡片已注册进内置卡片列表
        assertTrue(registry().allCards().stream().anyMatch(item -> "system.messaging-binding".equals(item.code())));
    }

    private static DashboardCardDefinition messagingBindingCard() {
        return registry().allCards().stream()
                .filter(item -> "system.messaging-binding".equals(item.code()))
                .findFirst()
                .orElseThrow();
    }

    private static DashboardCardRegistry registry() {
        return new DashboardCardRegistry(new EmptyPluginGateway(), new EmptyCapabilityModules(), List.of());
    }

    private static final class EmptyPluginGateway implements PluginRuntimeGateway {

        @Override
        public List<PluginDescriptorInfo> discover() {
            return List.of();
        }

        @Override
        public Optional<PluginDescriptorInfo> describe(Path jarPath) {
            return Optional.empty();
        }

        @Override
        public void load(PluginModule module) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enable(PluginModule module) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disable(String code) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unload(String code) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean loaded(String code) {
            return false;
        }

        @Override
        public boolean enabled(String code) {
            return false;
        }

        @Override
        public List<PluginPermissionInfo> permissions(String code) {
            return List.of();
        }

        @Override
        public List<PluginFrontendModuleInfo> frontendModules() {
            return List.of();
        }

        @Override
        public List<PluginDashboardCardInfo> dashboardCards() {
            return List.of();
        }

        @Override
        public List<PluginHttpEndpointInfo> httpEndpoints() {
            return List.of();
        }

        @Override
        public List<PluginCommandInfo> commands() {
            return List.of();
        }

        @Override
        public Optional<PluginFrontendAssetInfo> frontendAsset(String code, String assetPath) {
            return Optional.empty();
        }

        @Override
        public PluginHttpDispatchResult dispatch(PluginHttpDispatchRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PluginRuntimeAssets runtimeAssets(String code) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class EmptyCapabilityModules implements CapabilityModuleRepo {

        @Override
        public CapabilityModule save(CapabilityModule module) {
            return module;
        }

        @Override
        public Optional<CapabilityModule> findByCode(String code) {
            return Optional.empty();
        }

        @Override
        public List<CapabilityModule> findAll() {
            return List.of();
        }
    }
}
