package online.yudream.base.interfaces.platform.milky.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MilkyConnectionCreateRequest {
    @NotBlank
    private String name;
    private String protocol;
    private String baseUrl;
    private String token;
    private String appId;
    private String appSecret;
    private Boolean sandbox;
    private Integer intents;
    private String commandMenuImageMode;
    private String commandMenuPublicBaseUrl;
}
