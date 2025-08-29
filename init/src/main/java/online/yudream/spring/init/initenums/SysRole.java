package online.yudream.spring.init.initenums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import online.yudream.spring.entity.entity.Role;

@AllArgsConstructor
@ToString
@Getter
public enum SysRole {
    SUPER(Role.builder()
                    .id("super")
                    .level(0)
                    .name("sys.role.super.name")
                    .description("sys.role.super.description")
                    .enabled(true)
                    .build()
    ),
    ADMIN(
            Role.builder()
                    .id("admin")
                    .level(0)
                    .name("sys.role.admin.name")
                    .description("sys.role.admin.description")
                    .enabled(true)
                    .build()
    ),
    USER(
            Role.builder()
                    .id("user")
                    .level(3)
                    .name("sys.role.user.name")
                    .description("sys.role.user.description")
                    .enabled(true)
                    .build()
    )
    ;

    private final Role role;
}
