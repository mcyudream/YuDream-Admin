package online.yudream.base.application.system.user.cmd;

import lombok.Data;

import java.util.List;

@Data
public class UserRoleChangesCmd {
    private Long id;
    private List<String> expectedRoleIds;
    private List<String> grant;
    private List<String> revoke;
}
