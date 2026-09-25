package online.yudream.base.domain.system.backup.event;

/**
 * 备份计划变更事件：应用层在计划增删改/启停后发布，
 * 基础设施调度器监听并重新排布定时任务（解耦层间依赖）。
 */
public record BackupPlansChangedEvent() {
}
