package online.yudream.base.interfaces.platform.satori.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class SatoriInternalInvokeRequest {
    @NotBlank
    private String platform;
    @NotBlank
    private String userId;
    @NotBlank
    private String method;
    private Map<String, Object> payload;
}
