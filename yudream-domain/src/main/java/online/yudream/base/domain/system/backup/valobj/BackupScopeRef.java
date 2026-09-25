package online.yudream.base.domain.system.backup.valobj;

import online.yudream.base.domain.system.backup.enumerate.BackupScopeType;

import java.util.regex.Pattern;

/**
 * 备份范围引用。系统范围为固定值 {@code system}；
 * 插件范围以 {@code plugin:{pluginCode}/{scopeCode}} 标识，任务与清单中均使用该 tag。
 */
public record BackupScopeRef(BackupScopeType type, String pluginCode, String scopeCode, String displayName) {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    public static BackupScopeRef system() {
        return new BackupScopeRef(BackupScopeType.SYSTEM, null, "system", "系统数据");
    }

    public static BackupScopeRef plugin(String pluginCode, String scopeCode, String displayName) {
        if (pluginCode == null || !CODE_PATTERN.matcher(pluginCode).matches()) {
            throw new IllegalArgumentException("非法的插件代码：" + pluginCode);
        }
        if (scopeCode == null || !CODE_PATTERN.matcher(scopeCode).matches()) {
            throw new IllegalArgumentException("非法的备份范围代码：" + scopeCode);
        }
        return new BackupScopeRef(BackupScopeType.PLUGIN, pluginCode, scopeCode, displayName);
    }

    /** 解析任务/清单中的范围 tag；非法 tag 抛出业务异常。 */
    public static BackupScopeRef parse(String tag) {
        if ("system".equals(tag)) {
            return system();
        }
        if (tag != null && tag.startsWith("plugin:")) {
            String remainder = tag.substring("plugin:".length());
            int split = remainder.indexOf('/');
            if (split > 0 && split < remainder.length() - 1) {
                return plugin(remainder.substring(0, split), remainder.substring(split + 1), remainder);
            }
        }
        throw new online.yudream.base.domain.common.exception.BizException("非法的备份范围：" + tag);
    }

    public String tag() {
        return type == BackupScopeType.SYSTEM ? "system" : "plugin:" + pluginCode + "/" + scopeCode;
    }
}
