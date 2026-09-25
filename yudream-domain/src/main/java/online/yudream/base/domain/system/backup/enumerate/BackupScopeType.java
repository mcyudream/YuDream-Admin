package online.yudream.base.domain.system.backup.enumerate;

/** 备份范围类型。 */
public enum BackupScopeType {
    /** 宿主系统数据（Mongo 集合 + 对象存储）。 */
    SYSTEM,
    /** 插件经备份扩展点贡献的数据范围。 */
    PLUGIN
}
