package online.yudream.base.interfaces.installer.res;

import lombok.Builder;
import lombok.Data;

/**
 * 安装落盘结果响应。
 */
@Data
@Builder
public class InstallerApplyResultRes {

    private boolean restarting;
    private String bootstrapFileLocation;
    private long restartDelayMs;
}
