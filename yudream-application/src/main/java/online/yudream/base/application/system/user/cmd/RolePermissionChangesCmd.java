package online.yudream.base.application.system.user.cmd;

import lombok.Data;

import java.util.List;

@Data
public class RolePermissionChangesCmd {
    private Long id;
    private List<String> expectedPermissions;
    private List<String> grant;
    private List<String> revoke;
}
