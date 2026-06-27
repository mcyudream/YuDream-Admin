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
public class UserDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String qq;
    private LocalDateTime createTime;

}
