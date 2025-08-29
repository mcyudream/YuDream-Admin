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
            .description("网站管理组")
            .build())
    ;
    private final Department department;
}
