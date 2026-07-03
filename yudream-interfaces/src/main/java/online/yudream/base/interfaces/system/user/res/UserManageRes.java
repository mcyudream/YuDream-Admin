package online.yudream.base.interfaces.system.user.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.system.user.enumerate.UserStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class UserManageRes {
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
