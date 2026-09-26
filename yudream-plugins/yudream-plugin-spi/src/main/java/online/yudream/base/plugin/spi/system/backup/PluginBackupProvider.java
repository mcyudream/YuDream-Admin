package online.yudream.base.plugin.spi.system.backup;

/**
 * 插件备份范围提供者：插件通过
 * {@code context.registerExtension(PluginBackupProvider.class, implementation)}
 * 向宿主声明一个可全量导出、可按合并策略恢复的数据范围。
 * <p>
 * 注册随插件 disable/unload 自动回收；宿主在全量导出、远程异地备份与合并导入时
 * 按范围调用本接口。每个插件可注册多个不同 {@code scopeCode} 的提供者。
 * 导出与恢复过程中的异常会导致当前备份任务失败（fail-fast），宿主负责记录原因。
 *
 * @since 2.33.0
 */
public interface PluginBackupProvider {

    /** 范围代码，插件内唯一，须匹配 {@code [a-z0-9][a-z0-9-]{0,63}}。 */
    String scopeCode();

    /** 展示名称（中文）。 */
    String displayName();

    /** 范围说明（中文，可为空）。 */
    default String description() {
        return "";
    }

    /** 默认调度 cron 表达式；空表示不提供默认计划，由管理员在备份中心配置。 */
    default String defaultSchedule() {
        return "";
    }

    /**
     * 将本范围全部数据写入宿主提供的 {@link BackupSink}。
     * 只允许通过 sink 写文件，禁止感知归档格式与远端存储细节。
     * 局部跳过（如某节点离线、某数据源不可用）不应抛异常中断整个备份，
     * 而是记录到返回的 {@link ExportReport#warnings()}，宿主会随任务消息透出；
     * 只有整体无法继续时才抛异常（fail-fast，宿主丢弃本次归档）。
     */
    ExportReport export(BackupSink sink) throws Exception;

    /**
     * 按合并策略恢复此前由本范围导出的文件。source 只包含本范围的文件；
     * 策略语义与宿主系统数据一致：{@code LOCAL_WINS} 只补缺，{@code ARCHIVE_WINS} 覆盖同名。
     * 恢复是尽力而为：单条目失败建议跳过并留待整体异常上报，不要静默吞掉。
     */
    void restore(BackupSource source, PluginBackupConflictStrategy strategy) throws Exception;

    /** 导出结果报告：warnings 会拼进备份任务消息展示给管理员。 */
    record ExportReport(java.util.List<String> warnings) {

        public static ExportReport none() {
            return new ExportReport(java.util.List.of());
        }
    }
}
