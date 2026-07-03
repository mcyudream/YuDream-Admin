package online.yudream.base.infra.system.user.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.user.enumerate.PermissionSource;
import online.yudream.base.domain.system.user.enumerate.PermissionStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 权限数据对象。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Document(collection = "sysPermission")
public class PermissionDO extends BaseDO {

    @Indexed(unique = true)
    private String code;

    private String name;

    private String module;

    private String description;

    private PermissionStatus status;

    private PermissionSource source;
}
