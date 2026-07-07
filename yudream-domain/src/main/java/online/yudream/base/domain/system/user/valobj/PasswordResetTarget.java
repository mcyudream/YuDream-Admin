package online.yudream.base.domain.system.user.valobj;

/**
 * 密码重置 Token 指向的账号。
 */
public record PasswordResetTarget(Long userId, String email) {
}
