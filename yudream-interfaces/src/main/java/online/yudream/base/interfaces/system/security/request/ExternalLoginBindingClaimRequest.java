package online.yudream.base.interfaces.system.security.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExternalLoginBindingClaimRequest {
    @NotBlank(message = "第三方账号绑定凭证不能为空")
    private String bindingToken;
}
