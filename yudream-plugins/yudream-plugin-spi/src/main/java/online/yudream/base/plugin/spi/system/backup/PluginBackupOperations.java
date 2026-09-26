package online.yudream.base.plugin.spi.system.backup;

import java.util.Optional;

/**
 * 插件备份触发端口：插件（如实例计划任务、面板按钮）以本插件身份
 * 发起一次范围备份任务，任务由宿主队列串行执行并可推送异地目标。
 * 经 {@code context.framework().backups(pluginCode)} 获取，只能操作本插件的备份范围。
 *
 * @since 2.33.0
 */
public interface PluginBackupOperations {

    /**
     * 触发一次备份任务并立即返回任务 id（异步执行）。
     * 范围未注册、异地目标不存在或已停用时抛出宿主业务异常。
     */
    String startScopeBackup(PluginScopeBackupRequest request);

    /** 查询本插件触发的任务状态；任务不存在或不属于本插件时返回空。 */
    Optional<PluginBackupJobStatus> status(String jobId);
}
