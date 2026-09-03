package online.yudream.base.plugin.spi.system.auth;

/**
 * 身份核验结果。verified=true 表示该主体已完成核验；
 * verified=false 时 message 作为注册拒绝文案返回给用户，必须是正常中文文案
 * （例如"请先完成教育邮箱验证"或"人工审核中，审核通过后即可注册"）。
 */
public record IdentityVerificationResult(boolean verified, String message) {

    private static final IdentityVerificationResult PASSED = new IdentityVerificationResult(true, null);

    public static IdentityVerificationResult passed() {
        return PASSED;
    }

    public static IdentityVerificationResult unverified(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("未通过原因不能为空");
        }
        return new IdentityVerificationResult(false, message.trim());
    }
}
