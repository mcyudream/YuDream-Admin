package online.yudream.base.interfaces.system.security.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExternalLoginProviderSaveRequest {
    @NotBlank
    private String code;
    private String name;
    private String appId;
    private String appKey;
    @NotBlank
    private String callbackUrl;
    private String endpoint;
    private boolean enabled;
    private String supportedTypes;
}
