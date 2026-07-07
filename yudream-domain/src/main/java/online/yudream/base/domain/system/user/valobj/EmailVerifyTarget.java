package online.yudream.base.domain.system.user.valobj;

/**
 * 邮箱验证 Token 指向的账号。
 */
public record EmailVerifyTarget(Long userId, String email) {
}
