package online.yudream.base.application.system.template.cmd;

import online.yudream.base.domain.system.user.enumerate.UserStatus;

import java.util.List;

/** 用户模板行。密码与账号身份不参与对既有用户的覆盖。 */
public record TemplateUserImportCmd(
        Long sourceId,
        String username,
        String nickname,
        String email,
        String phone,
        String qq,
        Boolean emailVerified,
        UserStatus status,
        List<String> deptPaths,
        String defaultDeptPath,
        List<String> roleCodes
) {
}
