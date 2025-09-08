package online.yudream.spring.base.utils;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

import java.security.Permission;

@Component
public class PermissionUtils {
    public boolean isHasPermission(String permission) {
        try {
            StpUtil.checkPermission(permission);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
