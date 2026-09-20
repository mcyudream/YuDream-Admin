package online.yudream.base.interfaces.installer.res;

import lombok.Builder;
import lombok.Data;

/**
 * 安装器状态响应。
 */
@Data
@Builder
public class InstallerStatusRes {

    private boolean installerMode;
    private String bootstrapFileLocation;
    private boolean dockerDeployment;
    private String javaVersion;
    private boolean setupTokenRequired;
}
