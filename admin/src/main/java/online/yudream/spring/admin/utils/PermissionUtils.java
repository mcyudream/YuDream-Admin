package online.yudream.spring.admin.utils;

import cn.dev33.satoken.stp.StpUtil;
import online.yudream.spring.entity.entity.Department;
import org.springframework.stereotype.Component;

@Component
public class PermissionUtils {
    public boolean isHasDepartmentPermission(Department department, String option) {
        return StpUtil.hasPermission(department.getId()+"."+option);
    }
}
