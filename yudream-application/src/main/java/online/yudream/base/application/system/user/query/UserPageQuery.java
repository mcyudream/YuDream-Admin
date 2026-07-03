package online.yudream.base.application.system.user.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.domain.system.user.enumerate.UserStatus;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserPageQuery extends PageQuery {
    private String keyword;
    private Long deptId;
    private Long roleId;
    private Boolean emailVerified;
    private UserStatus status;
}
