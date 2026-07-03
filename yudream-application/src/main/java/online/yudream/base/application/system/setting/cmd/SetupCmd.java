package online.yudream.base.application.system.setting.cmd;

import lombok.Builder;
import lombok.Data;

/**
 * 系统初始化命令。
 */
@Data
@Builder
public class SetupCmd {

    /**
     * 站点名称。
     */
    private String siteName;

    /**
     * 管理员用户名。
     */
    private String adminUsername;

    /**
     * 管理员昵称（可选）。
     */
    private String adminNickname;

    /**
     * 管理员邮箱。
     */
    private String adminEmail;

    /**
     * 管理员密码。
     */
    private String adminPassword;

    /**
     * 确认密码。
     */
    private String adminConfirmPassword;
}
