package online.yudream.base.domain.platform.capability.valobj;

import online.yudream.base.domain.common.exception.BizException;

public record CapabilityCode(String value) {

    public CapabilityCode {
        if (value == null || value.trim().isEmpty()) {
            throw new BizException("能力编码不能为空");
        }
        value = value.trim();
    }

    public static CapabilityCode of(String value) {
        return new CapabilityCode(value);
    }
}
