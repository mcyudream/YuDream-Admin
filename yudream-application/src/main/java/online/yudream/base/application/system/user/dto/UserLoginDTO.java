package online.yudream.base.application.system.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserLoginDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String token;
    private String tokenName;
    private String refreshToken;
    private boolean dualTokenEnabled;
    private long expiresIn;
    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private boolean emailVerified;
    private String avatar;
    private LocalDateTime createTime;
}
