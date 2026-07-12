package online.yudream.base.interfaces.platform.milky.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class MilkyConnectionUpdateRequest { @NotBlank private String name; @NotBlank private String baseUrl; private String token; private String commandMenuImageMode; private String commandMenuPublicBaseUrl; }
