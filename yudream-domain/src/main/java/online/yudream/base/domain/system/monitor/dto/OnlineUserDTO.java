package online.yudream.base.domain.system.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineUserDTO {

    private String token;
    private Long userId;
    private String username;
    private String nickname;
    /** 会话用户头像 URL，可空（取不到时为 null）。 */
    private String avatar;
    private String email;
    private Long timeout;
    private Long activeTimeout;
    private String device;
}
