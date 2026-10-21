package online.yudream.base.domain.system.user.valobj;

/**
 * 初始密码设置 Token 指向的账号。
 * <p>
 * 用于外部导入/模板创建的新用户一次性设置初始密码：
 * 目标用户邮箱可以尚未验证，设置成功后邮箱标记为已验证。
 */
public record InitialPasswordSetupTarget(Long userId, String email) {
}
