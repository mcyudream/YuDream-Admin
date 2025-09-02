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
                    .name("超级管理员")
                    .description("最高管理员")
                    .enabled(true)
                    .build()
    ),
    ADMIN(
            Role.builder()
                    .id("admin")
                    .level(1)
                    .name("管理员")
                    .description("网站管理员")
                    .enabled(true)
                    .build()
    ),
    USER(
            Role.builder()
                    .id("user")
                    .level(3)
                    .name("用户")
                    .description("普通用户")
                    .enabled(true)
                    .build()
    )
    ;

    private final Role role;
}
