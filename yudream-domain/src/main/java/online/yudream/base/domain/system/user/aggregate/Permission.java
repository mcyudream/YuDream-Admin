package online.yudream.base.domain.system.user.aggregate;

import lombok.*;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.system.user.enumerate.PermissionStatus;
import online.yudream.base.domain.system.user.valobj.PermissionID;

@Getter
@NoArgsConstructor
public class Permission {
    private PermissionID id;
    private String name;
    private String module;
    private String description;
    private PermissionStatus status; // ACTIVE, DEPRECATED

    public static Permission create(String code, String name, String module, String desc) {
        Permission p = new Permission();
        p.id = PermissionID.of(code);
        p.name = name;
        p.module = module;
        p.description = desc;
        p.status = PermissionStatus.ACTIVE;
        return p;
    }

    public void update(String name, String module, String desc) {
        this.name = name;
        this.module = module;
        this.description = desc;
    }

    public void deprecate() {
        this.status = PermissionStatus.DEPRECATED;
    }

    public void activate() {
        this.status = PermissionStatus.ACTIVE;
    }

    public void setStatus(PermissionStatus status) {
        this.status = status;
    }

}
