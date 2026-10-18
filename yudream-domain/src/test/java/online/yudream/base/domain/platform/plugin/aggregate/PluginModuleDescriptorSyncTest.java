package online.yudream.base.domain.platform.plugin.aggregate;

import online.yudream.base.domain.platform.mobile.enumerate.MobilePlatform;
import online.yudream.base.domain.platform.mobile.valobj.MobilePluginSupport;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册表同步的变化判定：一致时不写库，这样并发读接口（分页与概览同时拉）
 * 不再各自产生一批版本自增写入，也就不会互撞乐观锁。
 */
class PluginModuleDescriptorSyncTest {

    @Test
    void matchesRightAfterRefresh() {
        PluginDescriptorInfo descriptor = descriptor("1.0.0", "演示插件");
        PluginModule module = PluginModule.fromDescriptor(descriptor);
        module.refreshDescriptor(descriptor);

        assertTrue(module.matchesDescriptor(descriptor));
    }

    @Test
    void detectsVersionNameAndDependencyChanges() {
        PluginDescriptorInfo descriptor = descriptor("1.0.0", "演示插件");
        PluginModule module = PluginModule.fromDescriptor(descriptor);

        assertFalse(module.matchesDescriptor(descriptor("1.0.1", "演示插件")));
        assertFalse(module.matchesDescriptor(descriptor("1.0.0", "改名后的插件")));
        assertFalse(module.matchesDescriptor(new PluginDescriptorInfo("demo", "演示插件", "1.0.0", null,
                null, null, List.of("dependency"), List.of())));
    }

    @Test
    void keepsRewritingRecordsWithoutStatus() {
        PluginDescriptorInfo descriptor = descriptor("1.0.0", "演示插件");
        PluginModule module = PluginModule.fromDescriptor(descriptor);
        // 历史记录 status 为空时仍需补写 INSTALLED，不能因为描述符一致就跳过
        module.setStatus(null);

        assertFalse(module.matchesDescriptor(descriptor));

        module.refreshDescriptor(descriptor);
        assertTrue(module.matchesDescriptor(descriptor));
    }

    @Test
    void detectsMobileDeclarationChanges() {
        PluginDescriptorInfo undeclared = descriptor("1.0.0", "演示插件");
        PluginModule module = PluginModule.fromDescriptor(undeclared);

        PluginDescriptorInfo declared = new PluginDescriptorInfo("demo", "演示插件", "1.0.0", null,
                null, null, List.of(), List.of(), null, null,
                MobilePluginSupport.declared(List.of(MobilePlatform.ANDROID), null, List.of(),
                        "移动端插件", null, null, null, null, null));

        assertFalse(module.matchesDescriptor(declared));

        module.refreshDescriptor(declared);
        assertTrue(module.matchesDescriptor(declared));
        // 回写后再读出来的形态（mobileSupport 由持久化字段重建）也必须判为一致
        assertTrue(PluginModule.fromDescriptor(declared).matchesDescriptor(declared));

        assertFalse(module.matchesDescriptor(undeclared));
    }

    @Test
    void blankOptionalFieldsNormalizeToSameShape() {
        PluginModule module = PluginModule.fromDescriptor(descriptor("1.0.0", "演示插件"));
        PluginDescriptorInfo withBlankIcon = new PluginDescriptorInfo("demo", "演示插件", "1.0.0", "演示用插件",
                "online.yudream.demo.DemoPlugin", "plugins/demo.jar", List.of(), List.of(), "  ", "  ");

        // icon/gitUrl 空白会被归一为 null，与未声明形态一致，不应触发写库
        assertTrue(module.matchesDescriptor(withBlankIcon));
        assertTrue(module.getStatus() == PluginStatus.INSTALLED);
    }

    private static PluginDescriptorInfo descriptor(String version, String name) {
        return new PluginDescriptorInfo("demo", name, version, "演示用插件",
                "online.yudream.demo.DemoPlugin", "plugins/demo.jar", List.of(), List.of());
    }
}
