package online.yudream.base.domain.platform.ai.aggregate;

import online.yudream.base.domain.platform.ai.valobj.AiToolModules;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统工具开关聚合：全局总开关 + 按模块开关。
 * 未登记的模块视为启用（新增模块默认可用），总开关关闭时所有模块工具都不可用。
 */
public class AiToolSettings {

    public static final String GLOBAL_KEY = "system-tools";

    private boolean systemEnabled;
    private Map<String, Boolean> moduleStates;

    private AiToolSettings(boolean systemEnabled, Map<String, Boolean> moduleStates) {
        this.systemEnabled = systemEnabled;
        this.moduleStates = moduleStates == null ? new LinkedHashMap<>() : new LinkedHashMap<>(moduleStates);
    }

    public static AiToolSettings defaults() {
        return new AiToolSettings(true, Map.of());
    }

    public static AiToolSettings of(boolean systemEnabled, Map<String, Boolean> moduleStates) {
        return new AiToolSettings(systemEnabled, moduleStates);
    }

    /** 全局总开关：关闭后所有系统工具（含 load_tools）不再提供给任何运行。 */
    public boolean isSystemEnabled() {
        return systemEnabled;
    }

    public Map<String, Boolean> getModuleStates() {
        return Map.copyOf(moduleStates);
    }

    /** 模块（含 MCP 服务模块）是否启用；未登记视为启用。 */
    public boolean isModuleEnabled(String moduleCode) {
        if (!systemEnabled || moduleCode == null || moduleCode.isBlank()) {
            return false;
        }
        Boolean state = moduleStates.get(moduleCode.trim());
        return state == null || state;
    }

    public void updateSystemEnabled(boolean enabled) {
        this.systemEnabled = enabled;
    }

    public void updateModuleEnabled(String moduleCode, boolean enabled) {
        if (moduleCode == null || moduleCode.isBlank()) {
            return;
        }
        String code = moduleCode.trim();
        if (AiToolModules.MODULE_META.equals(code)) {
            throw new online.yudream.base.domain.common.exception.BizException("工具目录模块用于按需加载，不能关闭");
        }
        moduleStates.put(code, enabled);
    }
}
