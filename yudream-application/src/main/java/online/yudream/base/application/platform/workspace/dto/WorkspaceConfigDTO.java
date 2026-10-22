package online.yudream.base.application.platform.workspace.dto;

/** 工作区全局设置：每用户配额（MB，管理员不限）与 tmp/ 自动清理天数。 */
public record WorkspaceConfigDTO(long quotaMb, int tmpRetentionDays) {
}
