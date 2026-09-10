package online.yudream.base.domain.platform.theme.service;

/**
 * 主题配置敏感字段加密端口：密文与主题/字段绑定，防止跨主题、跨字段重放。
 * 主题配置只写不解——管理端读取脱敏、公开端输出剔除，因此端口仅提供加密能力。
 */
public interface ThemeConfigSecretCipher {

    /** 是否已配置密钥材料，未配置时敏感字段无法保存。 */
    boolean canEncrypt();

    /** 判断值是否已是本端口产出的密文。 */
    boolean encrypted(String value);

    /** 以主题与字段为绑定材料加密明文。 */
    String encrypt(String themeCode, String fieldKey, String plaintext);
}
