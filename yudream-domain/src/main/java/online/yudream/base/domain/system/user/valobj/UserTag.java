package online.yudream.base.domain.system.user.valobj;

import online.yudream.base.domain.common.exception.BizException;

public record UserTag(String namespace, String code, String label) {

    public UserTag {
        if (namespace == null || namespace.isBlank()) {
            throw new BizException("标签命名空间不能为空");
        }
        if (code == null || code.isBlank()) {
            throw new BizException("标签编码不能为空");
        }
        if (label == null || label.isBlank()) {
            throw new BizException("标签名称不能为空");
        }
        namespace = namespace.trim();
        code = code.trim();
        label = label.trim();
    }

    public UserTag withNamespace(String forcedNamespace) {
        return new UserTag(forcedNamespace, code, label);
    }
}
