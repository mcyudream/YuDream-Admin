package online.yudream.base.domain.system.integration.enumerate;

/**
 * 系统集成配置分类：入库到 sys_setting 的 category 与其中的加密字段。
 */
public enum IntegrationCategory {

    MAIL("integration.mail", "mail.password"),
    STORAGE("integration.storage", "storage.secret-key");

    /** sys_setting 的 category 值。 */
    private final String category;
    /** 该分类中需要加密存储的 Setting key。 */
    private final String secretKey;

    IntegrationCategory(String category, String secretKey) {
        this.category = category;
        this.secretKey = secretKey;
    }

    public String category() {
        return category;
    }

    public boolean isSecretKey(String settingKey) {
        return secretKey.equals(settingKey);
    }
}
