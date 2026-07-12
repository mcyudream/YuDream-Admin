package online.yudream.base.interfaces.platform.milky.res;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
@Data @Builder
public class MilkyConnectionRes { private String id; private String name; private String baseUrl; private boolean enabled; private boolean credentialConfigured; private String commandMenuImageMode; private String commandMenuPublicBaseUrl; private LocalDateTime createTime; private LocalDateTime updateTime; }
