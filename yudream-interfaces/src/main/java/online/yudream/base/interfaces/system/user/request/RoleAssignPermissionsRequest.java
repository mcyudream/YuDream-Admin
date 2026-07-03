package online.yudream.base.interfaces.system.user.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class RoleAssignPermissionsRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private List<String> permissions = new ArrayList<>();
}
