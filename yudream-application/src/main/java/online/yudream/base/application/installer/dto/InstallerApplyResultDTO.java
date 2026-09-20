package online.yudream.base.application.installer.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 安装落盘结果 DTO：后端会在短暂延迟后自动退出，由容器重启策略拉起进入正常模式。
 */
@Data
@Builder
public class InstallerApplyResultDTO {

    private boolean restarting;
    private String bootstrapFileLocation;
    private long restartDelayMs;
}
