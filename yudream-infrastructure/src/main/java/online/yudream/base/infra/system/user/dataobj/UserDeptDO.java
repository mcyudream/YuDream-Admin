package online.yudream.base.infra.system.user.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDeptDO {

    private Long deptId;

    private Boolean defaultDept;
}
