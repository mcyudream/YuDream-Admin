package online.yudream.base.interfaces.system.setting.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import online.yudream.base.interfaces.common.validation.anno.PasswordRule;

/**
 * 系统初始化请求。
 */
@Data
public class SetupRequest {

    @NotBlank(message = "站点名称不能为空")
    private String siteName;

    @NotBlank(message = "管理员用户名不能为空")
    @Size(min = 3, message = "管理员用户名至少3位")
    private String adminUsername;

    private String adminNickname;

    @NotBlank(message = "管理员邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String adminEmail;

    @NotBlank(message = "管理员密码不能为空")
    @Size(min = 6, max = 18, message = "密码长度为6到18位")
    @PasswordRule
    private String adminPassword;

    @NotBlank(message = "确认密码不能为空")
    private String adminConfirmPassword;
}
