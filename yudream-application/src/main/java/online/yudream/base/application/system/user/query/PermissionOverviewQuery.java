package online.yudream.base.application.system.user.query;

import lombok.Data;

@Data
public class PermissionOverviewQuery {
    private String userKeyword;
    private Long userRoleId;
    private boolean userUnassignedOnly;
    private int userPage = 1;
    private int userSize = 30;
}
