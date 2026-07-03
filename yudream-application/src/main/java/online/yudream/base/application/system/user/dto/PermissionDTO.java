package online.yudream.base.application.system.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.user.enumerate.PermissionStatus;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String name;
    private String module;
    private String desc;
    private PermissionStatus status;
}
