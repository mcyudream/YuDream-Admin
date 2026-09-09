package online.yudream.base.domain.system.log.model;

import java.util.List;

/**
 * 系统日志模块筛选分组，例如「QQ 机器人 / 系统 / 平台」。
 */
public record SystemLogModuleGroup(String label, List<String> modules) {
    public SystemLogModuleGroup {
        label = label == null ? "" : label;
        modules = modules == null ? List.of() : List.copyOf(modules);
    }
}
