package online.yudream.base.application.platform.milky.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
@Data @Builder
public class MilkyConnectionDTO { private Long id; private String name; private String baseUrl; private boolean enabled; private boolean credentialConfigured; private String commandMenuImageMode; private String commandMenuPublicBaseUrl; private LocalDateTime createTime; private LocalDateTime updateTime; }
