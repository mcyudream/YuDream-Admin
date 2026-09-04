package online.yudream.base.domain.platform.form.valobj;

/**
 * 动态表单上传约束：能力配置键与默认值，infra provider 播种默认配置、application 运行时校验共用。
 */
public final class FormUploadConfig {

    public static final String CONFIG_MAX_UPLOAD_SIZE_MB = "maxUploadSizeMb";
    public static final long DEFAULT_MAX_UPLOAD_SIZE_MB = 100L;

    private FormUploadConfig() {
    }

    public static long parseMaxUploadSizeMb(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_MAX_UPLOAD_SIZE_MB;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : DEFAULT_MAX_UPLOAD_SIZE_MB;
        } catch (NumberFormatException ignored) {
            return DEFAULT_MAX_UPLOAD_SIZE_MB;
        }
    }
}
