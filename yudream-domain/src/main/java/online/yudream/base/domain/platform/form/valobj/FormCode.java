package online.yudream.base.domain.platform.form.valobj;

import online.yudream.base.domain.common.exception.BizException;

import java.util.Locale;
import java.util.regex.Pattern;

public record FormCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{1,63}$");

    public FormCode {
        if (value == null || value.trim().isEmpty()) {
            throw new BizException("表单编码不能为空");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(value).matches()) {
            throw new BizException("表单编码只能包含小写字母、数字、下划线和中划线，长度 2-64 位");
        }
    }

    public static FormCode of(String value) {
        return new FormCode(value);
    }
}
