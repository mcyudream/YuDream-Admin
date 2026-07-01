package online.yudream.base.infra.system.user.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * 角色数据对象。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sysRole")
public class RoleDO extends BaseDO {

    private String name;

    private Long deptId;

    private String code;

    private RoleLevel level;

    private boolean systemRole;

    private SystemRoleType systemType;

    private List<String> permissions;

    private RoleStatus status;
}
