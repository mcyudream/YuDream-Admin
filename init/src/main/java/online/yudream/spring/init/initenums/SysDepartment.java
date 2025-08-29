package online.yudream.spring.init.initenums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import online.yudream.spring.entity.entity.Department;

import java.util.List;

@AllArgsConstructor
@Getter
public enum SysDepartment {
    ADMIN(Department.builder()
            .id("admin")
            .name("管理组")
            .roles(
                    List.of(
                            SysRole.SUPER.getRole(),
                            SysRole.ADMIN.getRole()
                    )
            )
            .description("网站管理组")
            .build())
    ;
    private final Department department;
}
