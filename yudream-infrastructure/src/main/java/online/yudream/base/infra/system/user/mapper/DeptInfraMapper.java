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
        DeptDO deptDO = new DeptDO();
        deptDO.setId(dept.getId());
        deptDO.setName(dept.getName());
        deptDO.setDescription(dept.getDescription());
        deptDO.setLeaderId(dept.getLeader() == null ? null : dept.getLeader().getValue());
        deptDO.setPhone(dept.getPhone() == null ? null : dept.getPhone().getValue());
        deptDO.setParentId(dept.getParentId() == null ? null : dept.getParentId().getValue());
        deptDO.setSortOrder(dept.getSortOrder());
        deptDO.setDeptType(dept.getDeptType());
        deptDO.setStatus(dept.getStatus());
        deptDO.setVersion(dept.getVersion());
        deptDO.setCreateTime(dept.getCreateTime());
        deptDO.setUpdateTime(dept.getUpdateTime());
        return deptDO;
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
