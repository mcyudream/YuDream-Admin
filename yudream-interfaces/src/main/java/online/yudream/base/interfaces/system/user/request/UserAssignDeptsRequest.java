package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.Valid;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserAssignDeptsRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Valid
    private List<UserDeptAssignRequest> depts = new ArrayList<>();
}
