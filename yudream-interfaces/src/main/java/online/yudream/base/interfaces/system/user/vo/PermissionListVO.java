package online.yudream.base.interfaces.system.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户权限列表视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionListVO {

    private List<String> permissions;
}
