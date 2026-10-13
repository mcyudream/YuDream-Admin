package online.yudream.base.domain.system.user.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.valobj.UserPermissionStatistics;

/** 权限诊断只读投影；不读取用户联系方式、凭据和扩展字段。 */
public interface PermissionOverviewUserRepo {

    PageResult<User> page(String keyword, Long roleId, boolean unassignedOnly, int page, int size);

    UserPermissionStatistics statistics();
}
