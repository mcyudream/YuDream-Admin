package online.yudream.base.application.system.template.dto;

import online.yudream.base.domain.system.user.enumerate.UserStatus;

import java.util.List;

/** 可移植系统模板中的用户输出行，不含密码、令牌、头像和身份凭据。 */
public record TemplateUserDTO(
        String sourceId,
        String username,
        String nickname,
        String email,
        String phone,
        String qq,
        boolean emailVerified,
        UserStatus status,
        List<String> deptPaths,
        String defaultDeptPath,
        List<String> roleCodes
) {
}
