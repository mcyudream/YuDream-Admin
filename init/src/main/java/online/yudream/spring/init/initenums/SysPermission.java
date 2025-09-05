package online.yudream.spring.init.initenums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import online.yudream.spring.entity.entity.Permission;

@Getter
@AllArgsConstructor
public enum SysPermission {
    TEST_PERMISSION(Permission.builder()
            .id("test_permission")
            .name("测试权限")
            .description("仅用于测试")
            .build()),
    TEST_PERMISSION1(Permission.builder()
            .id("test_permission1")
            .name("测试权限1")
            .description("仅用于测试")
            .build()),
    TEST_PERMISSION2(Permission.builder()
            .id("test_permission1")
            .name("测试权限2")
            .description("仅用于测试")
            .build()),
    ;
    private final Permission permission;
}
