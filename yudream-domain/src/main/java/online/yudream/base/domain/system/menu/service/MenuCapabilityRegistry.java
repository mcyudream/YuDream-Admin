package online.yudream.base.domain.system.menu.service;

import java.util.Map;
import java.util.Optional;

/**
 * 菜单节点到平台能力的声明式归属（来自菜单注解 {@code capability}），供菜单与动态路由按能力启停裁剪。
 * 只读快照：系统菜单在代码中声明，运行期不变；与菜单持久化数据解耦，已有库中的菜单无需重新同步即可生效。
 */
public class MenuCapabilityRegistry {

    private final Map<String, String> capabilities;

    public MenuCapabilityRegistry(Map<String, String> capabilities) {
        this.capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
    }

    public Optional<String> capabilityOf(String menuCode) {
        return menuCode == null ? Optional.empty() : Optional.ofNullable(capabilities.get(menuCode));
    }
}
