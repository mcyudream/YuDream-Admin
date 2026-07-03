package online.yudream.base.application.system.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeptManageDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

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
    private List<DeptManageDTO> children = new ArrayList<>();
}
