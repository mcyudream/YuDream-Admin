package online.yudream.base.infra.system.user.mapper;

import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.UserID;
import online.yudream.base.domain.valobj.Phone;
import online.yudream.base.infra.system.user.dataobj.DeptDO;

/**
 * 部门领域对象与数据对象转换器。
 */
@NoArgsConstructor
public class DeptInfraMapper {

    public static DeptDO toDataObj(Dept dept) {
        if (dept == null) return null;
        return DeptDO.builder()
                .id(dept.getId())
                .name(dept.getName())
                .description(dept.getDescription())
                .leaderId(dept.getLeader() == null ? null : dept.getLeader().getValue())
                .phone(dept.getPhone() == null ? null : dept.getPhone().getValue())
                .parentId(dept.getParentId() == null ? null : dept.getParentId().getValue())
                .sortOrder(dept.getSortOrder())
                .deptType(dept.getDeptType())
                .status(dept.getStatus())
                .version(dept.getVersion())
                .createTime(dept.getCreateTime())
                .updateTime(dept.getUpdateTime())
                .build();
    }

    public static Dept toDomain(DeptDO deptDO) {
        if (deptDO == null) return null;
        return Dept.builder()
                .id(deptDO.getId())
                .name(deptDO.getName())
                .description(deptDO.getDescription())
                .leader(deptDO.getLeaderId() == null ? null : UserID.of(deptDO.getLeaderId()))
                .phone(deptDO.getPhone() == null ? null : Phone.fromTrusted(deptDO.getPhone()))
                .parentId(deptDO.getParentId() == null ? null : DeptID.of(deptDO.getParentId()))
                .sortOrder(deptDO.getSortOrder())
                .deptType(deptDO.getDeptType())
                .status(deptDO.getStatus())
                .version(deptDO.getVersion())
                .createTime(deptDO.getCreateTime())
                .updateTime(deptDO.getUpdateTime())
                .build();
    }
}
