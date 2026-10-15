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
import online.yudream.base.domain.system.user.valobj.DeptColorDefaults;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.valobj.HexColor;
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
    private Long avatarFileId;
    private String color;             // 主色 #rrggbb 小写 hex，可空（null=未设置）

    public static Dept create(Long id, String name, DeptID parentId, SystemDeptType type) {
        Dept d = new Dept();
        d.setId(id);
        d.name = name;
        d.parentId = parentId;
        d.deptType = type;
        d.sortOrder = 0;
        d.color = DeptColorDefaults.forName(name);
        d.status = DeptStatus.ACTIVE;
        return d;
    }

    /** 管理台创建部门：主色为空时按名称语义补默认色。 */
    public static Dept create(Long id, String name, String description, UserID leader, Phone phone,
                              DeptID parentId, Integer sortOrder, String color) {
        Dept d = new Dept();
        d.setId(id);
        d.name = name;
        d.description = description;
        d.leader = leader;
        d.phone = phone;
        d.parentId = parentId;
        d.sortOrder = sortOrder == null ? 0 : sortOrder;
        d.deptType = SystemDeptType.NORMAL;
        d.status = DeptStatus.ACTIVE;
        String normalized = HexColor.normalize(color);
        d.color = normalized == null ? DeptColorDefaults.forName(name) : normalized;
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

    public void updateAvatar(Long avatarFileId) {
        this.avatarFileId = avatarFileId;
    }

    /** 更新部门主色：空值表示未设置。 */
    public void updateColor(String color) {
        this.color = HexColor.normalize(color);
    }

    /** 启动回填：仅在主色未设置时补默认色，返回是否发生变化。 */
    public boolean fillColorIfAbsent(String color) {
        String normalized = HexColor.normalize(color);
        if (normalized == null || !isColorUnset()) {
            return false;
        }
        this.color = normalized;
        return true;
    }

    /** 主色是否未设置（null 或空白串）。 */
    public boolean isColorUnset() {
        return color == null || color.isBlank();
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
