package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserRoleChangesRequest {
    @NotNull(message = "请携带用户角色快照")
    private List<@NotBlank String> expectedRoleIds;

    @NotNull
    private List<@NotBlank String> grant = new ArrayList<>();

    @NotNull
    private List<@NotBlank String> revoke = new ArrayList<>();
}
