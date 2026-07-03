package online.yudream.base.domain.platform.cms.valobj;

import online.yudream.base.domain.common.exception.BizException;

public record PageSlug(String value) {

    public PageSlug {
        if (value == null || value.trim().isEmpty()) {
            throw new BizException("页面路径不能为空");
        }
        value = value.trim().toLowerCase();
        if (!value.matches("[a-z0-9][a-z0-9-_/]*")) {
            throw new BizException("页面路径只能包含小写字母、数字、中划线、下划线和斜杠");
        }
    }

    public static PageSlug of(String value) {
        return new PageSlug(value);
    }
}
