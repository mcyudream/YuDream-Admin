package online.yudream.base.infra.system.user.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 部门数据对象。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sysDept")
public class DeptDO extends BaseDO {

    private String name;

    private String description;

    private Long leaderId;

    private String phone;

    private Long parentId;

    private Integer sortOrder;

    private SystemDeptType deptType;

    private DeptStatus status;
}
