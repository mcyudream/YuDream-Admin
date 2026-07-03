package online.yudream.base.domain.system.security.valobj;

import java.util.Map;

public record OAuthClientUserInfo(
        String subject,
        String username,
        String nickname,
        String email,
        String avatar,
        Map<String, Object> raw
) {
}
