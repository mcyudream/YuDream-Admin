package online.yudream.base.interfaces.system.user.res;

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
public class UserRes implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String username;
    private String nickname;
    private String email;
    private String qq;
    private String phone;
    private LocalDateTime createTime;
}
