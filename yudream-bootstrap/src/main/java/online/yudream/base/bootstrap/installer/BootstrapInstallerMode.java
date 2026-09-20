package online.yudream.base.bootstrap.installer;

import online.yudream.base.domain.installer.BootstrapConfigFile;

/**
 * 安装器模式判定：无引导配置文件且未预置数据库配置时进入安装向导。
 */
public final class BootstrapInstallerMode {

    private BootstrapInstallerMode() {
    }

    public static boolean active(String[] args) {
        return BootstrapConfigFile.installerModeFromSystem(args);
    }
}
