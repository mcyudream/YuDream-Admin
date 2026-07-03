package online.yudream.base.domain.system.user.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.valobj.Phone;
import online.yudream.base.domain.system.user.valobj.UserID;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class Dept extends BaseDomain {

    private String name;
    private String description;
    private UserID leader;
    private Phone phone;
    private DeptID parentId;
    private Integer sortOrder;
    private SystemDeptType deptType;
    private DeptStatus status;

    public static Dept create(Long id, String name, DeptID parentId, SystemDeptType type) {
        Dept d = new Dept();
        d.setId(id);
        d.name = name;
        d.parentId = parentId;
        d.deptType = type;
        d.sortOrder = 0;
        d.status = DeptStatus.ACTIVE;
        return d;
    }

    public boolean isSystem() {
        return deptType == SystemDeptType.SYSTEM || deptType == SystemDeptType.SYSTEM_ADMIN;
    }

    public boolean isRoot() {
        return deptType == SystemDeptType.ROOT;
    }

    public void updateBasic(String name, String description, UserID leader, Phone phone, DeptID parentId, Integer sortOrder) {
        if (getId() != null && parentId != null && getId().equals(parentId.getValue())) {
            throw new BizException("上级部门不能选择自己");
        }
        this.name = name;
        this.description = description;
        this.leader = leader;
        this.phone = phone;
        this.parentId = parentId;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
    }

    public void activate() {
        this.status = DeptStatus.ACTIVE;
    }

    public void deactivate() {
        if (isSystem() || isRoot()) {
            throw new BizException("系统部门不可停用");
        }
        this.status = DeptStatus.DEPRECATED;
    }
}
