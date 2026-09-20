package online.yudream.base.application.system.setting.cmd;

import lombok.Data;

/**
 * 邮件集成配置保存命令。
 * 非密文字段：null 表示保持不变，空串表示清除（回落环境变量）；密码（密文）：留空=保持已存值。
 */
@Data
public class MailConfigCmd {

    private String host;
    private Integer port;
    private String username;
    private String password;
    private String from;
    private Boolean ssl;
    private Boolean starttls;
}
