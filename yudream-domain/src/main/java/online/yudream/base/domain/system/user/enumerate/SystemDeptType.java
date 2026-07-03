package online.yudream.base.domain.system.user.enumerate;

public enum SystemDeptType {
    NORMAL,      // 普通业务部门
    SYSTEM,      // 系统虚拟部门
    SYSTEM_ADMIN, // 系统-管理层（超管+管理员挂靠）
    ROOT         // 根/最大部门（用户+访客默认归属）
}
