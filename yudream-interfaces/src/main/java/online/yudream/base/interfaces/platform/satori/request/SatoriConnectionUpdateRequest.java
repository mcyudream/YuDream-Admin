package online.yudream.base.interfaces.platform.satori.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SatoriConnectionUpdateRequest {
    @NotBlank(message = "连接名称不能为空")
    private String name;
    @NotBlank(message = "Satori 地址不能为空")
    private String baseUrl;
    @NotBlank(message = "Satori 平台不能为空")
    private String platform;
    @NotBlank(message = "Satori 用户 ID 不能为空")
    private String userId;
    /** Omit or leave blank to retain the existing token. */
    private String token;
}
