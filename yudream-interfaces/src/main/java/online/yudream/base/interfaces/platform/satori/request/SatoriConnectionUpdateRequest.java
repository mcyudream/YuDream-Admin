package online.yudream.base.interfaces.platform.satori.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SatoriConnectionUpdateRequest {
    @NotBlank(message = "连接名称不能为空")
    private String name;
    @NotBlank(message = "Satori 地址不能为空")
    private String baseUrl;
    /** Omit or leave blank to retain the existing token. */
    private String token;
}
