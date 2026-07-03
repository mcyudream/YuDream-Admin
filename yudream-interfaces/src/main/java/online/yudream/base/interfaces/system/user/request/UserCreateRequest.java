package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.interfaces.common.validation.anno.PasswordRule;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserCreateRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    private String username;
    private String nickname;
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    private String phone;
    private String qq;
    @NotBlank(message = "密码不能为空")
    @PasswordRule
    private String password;
    private boolean emailVerified;
    private List<Long> roleIds = new ArrayList<>();
    @Valid
    private List<UserDeptAssignRequest> depts = new ArrayList<>();
}
