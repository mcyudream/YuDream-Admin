package online.yudream.base.application.system.setting.cmd;

import lombok.Data;

/**
 * 候选邮件配置测试命令：对给定配置发送测试邮件（不落库）。
 */
@Data
public class MailTestCmd {

    private String host;
    private Integer port;
    private String username;
    /** 留空则使用已保存/环境变量中的密码。 */
    private String password;
    private String from;
    private Boolean ssl;
    private Boolean starttls;
    private String to;
}
