package online.yudream.base.application.system.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import online.yudream.base.domain.system.user.enumerate.UserStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserManageDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String qq;
    private boolean emailVerified;
    private UserStatus status;
    @Builder.Default
    private List<Long> roleIds = new ArrayList<>();
    @Builder.Default
    private List<String> roleNames = new ArrayList<>();
    @Builder.Default
    private List<Long> deptIds = new ArrayList<>();
    @Builder.Default
    private List<String> deptNames = new ArrayList<>();
    private Long defaultDeptId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
