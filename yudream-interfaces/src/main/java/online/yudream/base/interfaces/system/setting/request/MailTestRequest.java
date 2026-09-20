package online.yudream.base.interfaces.system.setting.request;

import lombok.Data;

/**
 * 候选邮件配置测试请求（不落库，直接发送测试邮件）。
 */
@Data
public class MailTestRequest {

    private String host;
    private Integer port;
    private String username;
    /** 留空则使用已保存/环境变量中的密码。 */
    private String password;
    private String from;
    private Boolean ssl;
    private Boolean starttls;
    private String to;
    /** 仅安装窗口期匿名调用时需要。 */
    private String setupToken;
}
