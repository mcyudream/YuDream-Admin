package online.yudream.base.interfaces.system.user.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileRes {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String qq;
    private boolean emailVerified;
    private String avatar;
    private Long avatarFileId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
