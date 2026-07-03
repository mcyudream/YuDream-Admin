package online.yudream.base.interfaces.system.setting.assembler;

import online.yudream.base.application.system.setting.cmd.SetupCmd;
import online.yudream.base.interfaces.system.setting.request.SetupRequest;

/**
 * 初始化请求参数转换。
 */
public class SetupWebAssembler {

    public static SetupCmd toCmd(SetupRequest request) {
        if (request == null) {
            return null;
        }
        return SetupCmd.builder()
                .siteName(request.getSiteName())
                .adminUsername(request.getAdminUsername())
                .adminNickname(request.getAdminNickname())
                .adminEmail(request.getAdminEmail())
                .adminPassword(request.getAdminPassword())
                .adminConfirmPassword(request.getAdminConfirmPassword())
                .build();
    }
}
