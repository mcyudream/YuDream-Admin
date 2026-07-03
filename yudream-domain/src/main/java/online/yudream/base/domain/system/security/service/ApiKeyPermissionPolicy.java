package online.yudream.base.domain.system.security.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.valobj.PermissionScope;

import java.util.List;

public class ApiKeyPermissionPolicy {

    public void validateCreatorScope(PermissionScope requestedScope,
                                     List<String> creatorPermissions,
                                     boolean superAdmin) {
        if (requestedScope == null) {
            throw new BizException("API Key 权限范围不能为空");
        }
        if (superAdmin || requestedScope.isWithin(creatorPermissions)) {
            return;
        }
        throw new BizException("API Key 权限不能超过创建人的权限范围");
    }
}
