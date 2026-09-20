package online.yudream.base.application.installer.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 安装器状态 DTO。
 */
@Data
@Builder
public class InstallerStatusDTO {

    private boolean installerMode;
    private String bootstrapFileLocation;
    private boolean dockerDeployment;
    private String javaVersion;
    private boolean setupTokenRequired;
}
