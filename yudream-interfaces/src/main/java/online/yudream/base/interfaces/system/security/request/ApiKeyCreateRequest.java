package online.yudream.base.interfaces.system.security.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ApiKeyCreateRequest {

    @NotBlank(message = "API Key 名称不能为空")
    private String name;

    @NotEmpty(message = "API Key 权限范围不能为空")
    private List<String> permissions = new ArrayList<>();

    private LocalDateTime expireTime;
}
