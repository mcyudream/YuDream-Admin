package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RolePermissionChangesRequest {
    @NotNull(message = "请携带角色授权快照")
    private List<@NotNull String> expectedPermissions;

    @NotNull
    private List<@NotBlank String> grant = new ArrayList<>();

    @NotNull
    private List<@NotBlank String> revoke = new ArrayList<>();
}
