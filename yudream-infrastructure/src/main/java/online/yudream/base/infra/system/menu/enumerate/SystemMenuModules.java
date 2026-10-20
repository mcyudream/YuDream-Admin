package online.yudream.base.infra.system.menu.enumerate;

import online.yudream.base.infra.platform.menu.enumerate.ChatMenuModule;
import online.yudream.base.infra.platform.menu.enumerate.PlatformMenuModule;

import java.util.List;

/**
 * 宿主内置菜单枚举清单：菜单同步与能力归属索引共用同一份来源。
 */
public final class SystemMenuModules {

    public static final List<Class<? extends Enum<?>>> CLASSES = List.of(
            SystemMenuModule.class,
            PlatformMenuModule.class,
            ChatMenuModule.class);

    private SystemMenuModules() {
    }
}
