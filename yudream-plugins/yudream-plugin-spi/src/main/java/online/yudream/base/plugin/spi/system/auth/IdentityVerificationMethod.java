package online.yudream.base.plugin.spi.system.auth;

/**
 * 一种身份核验方式的展示元数据。code 是稳定唯一编码（如 xuexin、edu-email、
 * carsi、manual-review），displayName/icon/sort 供注册页与站点渲染选择入口。
 */
public record IdentityVerificationMethod(String code, String displayName, String description, String icon, int sort) {
}
