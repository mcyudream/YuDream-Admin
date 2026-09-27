package online.yudream.base.application.platform.mobile.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.mobile.cmd.MobileDeviceRegisterCmd;
import online.yudream.base.application.platform.mobile.cmd.MobileDeviceUnregisterCmd;
import online.yudream.base.application.platform.mobile.dto.MobileDeviceDTO;
import online.yudream.base.application.platform.mobile.dto.MobileManifestDTO;
import online.yudream.base.application.platform.mobile.query.MobileManifestQuery;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.mobile.enumerate.MobilePlatform;
import online.yudream.base.domain.platform.mobile.aggregate.MobileDevice;
import online.yudream.base.domain.platform.mobile.repo.MobileDeviceRepo;
import online.yudream.base.domain.platform.mobile.valobj.MobileHomeCard;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import online.yudream.base.domain.platform.plugin.repo.PluginModuleRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchRequest;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpEndpointInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginRuntimeAssets;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobileAppServiceTest {

    private static final String CAPABILITY_CODE = "mobile-app";

    @Test
    void manifestExcludesDisabledAndUndeclaredPlugins() {
        MobileTestHarness harness = MobileTestHarness.create(true, iosEnabled(null));
        harness.plugin("enabled-mobile", PluginStatus.ENABLED, List.of("android", "ios"), "2.0.0", List.of());
        harness.plugin("disabled-mobile", PluginStatus.DISABLED, List.of("android", "ios"), "2.0.0", List.of());
        harness.plugin("enabled-desktop-only", PluginStatus.ENABLED, null, null, null);

        MobileManifestDTO manifest = harness.service.manifest(query("android", "2.0.0", ""));

        assertEquals(List.of("enabled-mobile"), manifest.getEntries().stream()
                .map(entry -> entry.getCode()).toList());
    }

    @Test
    void manifestIosScenarioServesDeclaredIosPluginWhenSeatEnabled() {
        MobileTestHarness harness = MobileTestHarness.create(true, iosEnabled("true"));
        harness.plugin("travel", PluginStatus.ENABLED, List.of("android", "ios"), "2.1.0", List.of("push"));
        harness.plugin("android-only", PluginStatus.ENABLED, List.of("android"), "1.0.0", List.of());

        MobileManifestDTO ios = harness.service.manifest(query("ios", "2.1.0", "push"));
        assertEquals(1, ios.getEntries().size());
        var entry = ios.getEntries().get(0);
        assertEquals("travel", entry.getCode());
        assertEquals("1.2.3", entry.getVersion());
        assertEquals("rev-1", entry.getAssetRevision());
        assertEquals("feedc0ffee", entry.getRemoteEntrySha256());
        assertEquals("/api/platform/plugins/travel/assets/mobile/remoteEntry.js", entry.getRemoteEntryUrl());
        assertEquals("2.1.0", entry.getMinHostVersion());
        assertEquals(List.of("android", "ios"), entry.getPlatforms());
        assertEquals(List.of("push"), entry.getRequiredNativeCapabilities());
        // style 产物尚不存在 → styleUrl 为空
        assertNull(entry.getStyleUrl());

        // iOS 宿主版本低于插件下限 → 过滤
        assertTrue(harness.service.manifest(query("ios", "2.0.9", "push")).getEntries().isEmpty());
        // iOS 缺少要求的原生能力 → 过滤
        assertTrue(harness.service.manifest(query("ios", "3.0.0", "")).getEntries().isEmpty());
        // android 查询（宿主版本满足两家下限）包含 android-only 插件
        Set<String> androidCodes = harness.service.manifest(query("android", "2.1.0", "push")).getEntries().stream()
                .map(item -> item.getCode()).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("travel", "android-only"), androidCodes);
    }

    @Test
    void manifestIosSeatHiddenWhenCapabilityConfigNotEnabled() {
        // 配置键缺失（空配置行）→ 运行时回落 false，iOS 席位对外不存在
        MobileTestHarness harness = MobileTestHarness.create(true, iosEnabled(null));
        harness.plugin("travel", PluginStatus.ENABLED, List.of("android", "ios"), "2.1.0", List.of());

        assertTrue(harness.service.manifest(query("ios", "9.9.9", "")).getEntries().isEmpty());
        assertEquals(1, harness.service.manifest(query("android", "9.9.9", "")).getEntries().size());

        // 显式关闭同理
        harness.capabilityModules.put(CAPABILITY_CODE, capabilityModule("false"));
        assertTrue(harness.service.manifest(query("ios", "9.9.9", "")).getEntries().isEmpty());
    }

    @Test
    void manifestTransmitsDisplayAndHomeCardDeclarations() {
        MobileTestHarness harness = MobileTestHarness.create(true, iosEnabled("true"));
        harness.plugin("forum", PluginStatus.ENABLED, List.of("android", "ios"), "2.0.0", List.of(),
                "论坛", "社区讨论", "chatbubbles-outline",
                List.of(new MobileHomeCard("latest-posts", "最新帖子", "社区最新动态", "flame-outline", "/posts/latest"),
                        new MobileHomeCard("hot-posts", "热门帖子", "", "", "/posts/hot")));

        MobileManifestDTO manifest = harness.service.manifest(query("android", "2.0.0", ""));
        var entry = manifest.getEntries().stream()
                .filter(item -> item.getCode().equals("forum")).findFirst().orElseThrow();
        assertEquals("论坛", entry.getName());
        assertEquals("社区讨论", entry.getDescription());
        assertEquals("chatbubbles-outline", entry.getIcon());
        assertEquals(2, entry.getHomeCards().size());
        var firstCard = entry.getHomeCards().get(0);
        assertEquals("latest-posts", firstCard.getId());
        assertEquals("最新帖子", firstCard.getTitle());
        assertEquals("社区最新动态", firstCard.getDescription());
        assertEquals("flame-outline", firstCard.getIcon());
        assertEquals("/posts/latest", firstCard.getRoute());
        // 空描述/图标按未声明处理，下发 null
        assertNull(entry.getHomeCards().get(1).getDescription());
        assertNull(entry.getHomeCards().get(1).getIcon());
        assertEquals("/posts/hot", entry.getHomeCards().get(1).getRoute());

        // 未声明展示与主页卡片的插件：字段保持 null/空列表，过滤不受影响
        harness.plugin("plain", PluginStatus.ENABLED, List.of("android"), "1.0.0", List.of());
        var plain = harness.service.manifest(query("android", "2.0.0", "")).getEntries().stream()
                .filter(item -> item.getCode().equals("plain")).findFirst().orElseThrow();
        assertNull(plain.getName());
        assertNull(plain.getDescription());
        assertNull(plain.getIcon());
        assertTrue(plain.getHomeCards().isEmpty());
    }

    @Test
    void manifestRejectsWhenCapabilityGateClosed() {
        MobileTestHarness harness = MobileTestHarness.create(false, iosEnabled("true"));
        BizException exception = assertThrows(BizException.class,
                () -> harness.service.manifest(query("android", "2.0.0", "")));
        assertTrue(exception.getMessage().contains("移动应用"));
    }

    @Test
    void manifestFailsOnIllegalPlatform() {
        MobileTestHarness harness = MobileTestHarness.create(true, iosEnabled("true"));
        assertThrows(BizException.class, () -> harness.service.manifest(query("harmony", "1.0.0", "")));
    }

    @Test
    void registerUpsertsIdempotentlyByUserAndToken() {
        MobileTestHarness harness = MobileTestHarness.create(true, iosEnabled("true"));
        MobileDeviceRegisterCmd first = new MobileDeviceRegisterCmd(
                MobilePlatform.ANDROID, "fcm", "token-1", "Pixel 8", "1.2.0", "2.0.0");
        MobileDeviceDTO created = harness.service.register(100L, first);
        assertNotNull(created.getId());
        assertEquals("android", created.getPlatform());
        assertNotNull(created.getRegisteredAt());

        MobileDeviceRegisterCmd second = new MobileDeviceRegisterCmd(
                MobilePlatform.IOS, "apns", "token-1", "iPhone 15", "1.3.0", "2.1.0");
        MobileDeviceDTO refreshed = harness.service.register(100L, second);
        assertEquals(created.getId(), refreshed.getId());
        assertEquals("ios", refreshed.getPlatform());
        assertEquals("apns", refreshed.getPushChannel());
        assertEquals(created.getRegisteredAt(), refreshed.getRegisteredAt());
        assertTrue(!refreshed.getLastSeenAt().isBefore(created.getRegisteredAt()));
        assertEquals(1, harness.devices.size());

        // 其他用户同 token 是另一条记录
        harness.service.register(200L, first);
        assertEquals(2, harness.devices.size());
    }

    @Test
    void unregisterMarksDeletedAndHidesFromList() {
        MobileTestHarness harness = MobileTestHarness.create(true, iosEnabled("true"));
        MobileDeviceDTO registered = harness.service.register(100L, new MobileDeviceRegisterCmd(
                MobilePlatform.ANDROID, "fcm", "token-1", null, "1.0.0", "2.0.0"));
        String originalId = registered.getId();
        assertEquals(1, harness.service.listByUser(100L).size());

        harness.service.unregister(100L, new MobileDeviceUnregisterCmd("token-1"));
        assertTrue(harness.service.listByUser(100L).isEmpty());
        // 重复注销同一 token：已不可见，再注销报未找到
        assertThrows(BizException.class, () -> harness.service.unregister(100L, new MobileDeviceUnregisterCmd("token-1")));
        // 注销后重新注册得到新记录（幂等 upsert 只对活跃记录生效）
        MobileDeviceDTO reRegistered = harness.service.register(100L, new MobileDeviceRegisterCmd(
                MobilePlatform.ANDROID, "fcm", "token-1", null, "1.0.0", "2.0.0"));
        assertNotEquals(originalId, reRegistered.getId());
    }

    private static MobileManifestQuery query(String platform, String hostVersion, String capabilitySet) {
        return new MobileManifestQuery(MobilePlatform.fromToken(platform), hostVersion,
                capabilitySet == null || capabilitySet.isBlank()
                        ? Set.of()
                        : java.util.Arrays.stream(capabilitySet.split(",")).collect(java.util.stream.Collectors.toSet()));
    }

    private static Map<String, String> iosEnabled(String value) {
        Map<String, String> config = new HashMap<>();
        config.put("iosEnabled", value);
        return config;
    }

    private static CapabilityModule capabilityModule(String iosEnabled) {
        Map<String, String> config = new HashMap<>();
        config.put("iosEnabled", iosEnabled);
        return CapabilityModule.builder().code(CAPABILITY_CODE).config(config).build();
    }

    private static final class MobileTestHarness {

        private final MobileAppService service;
        private final Map<String, CapabilityModule> capabilityModules = new LinkedHashMap<>();
        private final Map<String, PluginModule> pluginModules = new LinkedHashMap<>();
        private final List<MobileDevice> devices = new ArrayList<>();
        private final AtomicLong idSequence = new AtomicLong(1000);

        private MobileTestHarness(boolean capabilityEnabled, Map<String, String> capabilityConfig) {
            capabilityModules.put(CAPABILITY_CODE, CapabilityModule.builder()
                    .code(CAPABILITY_CODE)
                    .config(new HashMap<>(capabilityConfig))
                    .build());
            StubCapabilityGate gate = new StubCapabilityGate(capabilityEnabled);
            StubPluginRuntimeGateway gateway = new StubPluginRuntimeGateway();
            this.service = new MobileAppService(gate, new StubCapabilityModuleRepo(), new StubPluginModuleRepo(),
                    gateway, new StubMobileDeviceRepo());
        }

        static MobileTestHarness create(boolean capabilityEnabled, Map<String, String> capabilityConfig) {
            return new MobileTestHarness(capabilityEnabled, capabilityConfig);
        }

        void plugin(String code, PluginStatus status, List<String> platforms, String minHostVersion,
                    List<String> capabilities) {
            plugin(code, status, platforms, minHostVersion, capabilities, null, null, null, null);
        }

        void plugin(String code, PluginStatus status, List<String> platforms, String minHostVersion,
                    List<String> capabilities, String displayName, String description, String icon,
                    List<MobileHomeCard> homeCards) {
            PluginModule.PluginModuleBuilder builder = PluginModule.builder()
                    .id(idSequence.incrementAndGet())
                    .code(code)
                    .pluginVersion("1.2.3")
                    .status(status);
            if (platforms != null) {
                builder.mobilePlatforms(platforms.stream().map(MobilePlatform::fromToken).toList())
                        .mobileMinHostVersion(minHostVersion)
                        .mobileRequiredNativeCapabilities(capabilities)
                        .mobileName(displayName)
                        .mobileDescription(description)
                        .mobileIcon(icon)
                        .mobileHomeCards(homeCards);
            }
            pluginModules.put(code, builder.build());
        }

        private final class StubCapabilityGate extends CapabilityAppService {
            private final boolean enabled;

            private StubCapabilityGate(boolean enabled) {
                super(null, List.of());
                this.enabled = enabled;
            }

            @Override
            public boolean enabled(String code) {
                return enabled;
            }
        }

        private final class StubCapabilityModuleRepo implements CapabilityModuleRepo {
            @Override
            public CapabilityModule save(CapabilityModule module) {
                capabilityModules.put(module.getCode(), module);
                return module;
            }

            @Override
            public Optional<CapabilityModule> findByCode(String code) {
                return Optional.ofNullable(capabilityModules.get(code));
            }

            @Override
            public List<CapabilityModule> findAll() {
                return List.copyOf(capabilityModules.values());
            }
        }

        private final class StubPluginModuleRepo implements PluginModuleRepo {
            @Override
            public PluginModule save(PluginModule module) {
                pluginModules.put(module.getCode(), module);
                return module;
            }

            @Override
            public Optional<PluginModule> findByCode(String code) {
                return Optional.ofNullable(pluginModules.get(code));
            }

            @Override
            public List<PluginModule> findAll() {
                return List.copyOf(pluginModules.values());
            }

            @Override
            public void deleteByCode(String code) {
                pluginModules.remove(code);
            }
        }

        private final class StubMobileDeviceRepo implements MobileDeviceRepo {
            @Override
            public MobileDevice save(MobileDevice device) {
                if (device.getId() == null) {
                    device.setId(idSequence.incrementAndGet());
                }
                devices.removeIf(existing -> existing.getId().equals(device.getId()));
                devices.add(device);
                return device;
            }

            @Override
            public Optional<MobileDevice> findById(Long id) {
                return devices.stream().filter(device -> id.equals(device.getId())).findFirst();
            }

            @Override
            public Optional<MobileDevice> findByUserIdAndPushToken(Long userId, String pushToken) {
                return devices.stream()
                        .filter(device -> !device.deleted())
                        .filter(device -> userId.equals(device.getUserId()) && pushToken.equals(device.getPushToken()))
                        .findFirst();
            }

            @Override
            public List<MobileDevice> findByUserId(Long userId) {
                return devices.stream()
                        .filter(device -> !device.deleted() && userId.equals(device.getUserId()))
                        .toList();
            }
        }
    }

    /** 只覆盖 mobile 用例需要的网关方法，其余交由默认实现。 */
    private static final class StubPluginRuntimeGateway implements PluginRuntimeGateway {

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
        }

        @Override
        public void enable(PluginModule module) {
        }

        @Override
        public void disable(String code) {
        }

        @Override
        public void unload(String code) {
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
        public List<online.yudream.base.domain.platform.plugin.valobj.PluginPermissionInfo> permissions(String code) {
            return List.of();
        }

        @Override
        public List<PluginFrontendModuleInfo> frontendModules() {
            PluginFrontendModuleInfo travel = new PluginFrontendModuleInfo(
                    "travel", "/api/platform/plugins/travel/assets/remoteEntry.js", "travel", "2.26.0",
                    "integrity", "", "", 0, List.of(), null, List.of(), List.of(), "rev-1");
            return List.of(travel);
        }

        @Override
        public List<online.yudream.base.domain.platform.plugin.valobj.PluginDashboardCardInfo> dashboardCards() {
            return List.of();
        }

        @Override
        public List<PluginHttpEndpointInfo> httpEndpoints() {
            return List.of();
        }

        @Override
        public List<online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo> commands() {
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

        @Override
        public Optional<String> mobileAssetSha256(String code, String assetPath) {
            if ("remoteEntry.js".equals(assetPath)) {
                return Optional.of("feedc0ffee");
            }
            return Optional.empty();
        }
    }
}
