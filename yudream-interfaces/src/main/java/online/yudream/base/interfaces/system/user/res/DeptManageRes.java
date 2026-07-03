package online.yudream.base.interfaces.system.user.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class DeptManageRes {
    private Long id;
    private String name;
    private String description;
    private Long leaderId;
    private String leaderName;
    private String phone;
    private Long parentId;
    private Integer sortOrder;
    private SystemDeptType deptType;
    private DeptStatus status;
    private boolean systemDept;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @Builder.Default
    private List<DeptManageRes> children = new ArrayList<>();
}
