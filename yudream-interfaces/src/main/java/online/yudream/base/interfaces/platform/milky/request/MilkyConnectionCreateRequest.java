package online.yudream.base.interfaces.platform.milky.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class MilkyConnectionCreateRequest { @NotBlank private String name; @NotBlank private String baseUrl; @NotBlank private String token; private String commandMenuImageMode; private String commandMenuPublicBaseUrl; }
