package online.yudream.base.application.system.security.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ApiKeyCreateCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private List<String> permissions = new ArrayList<>();
    private LocalDateTime expireTime;
    private Long creatorUserId;
    private List<String> creatorPermissions = new ArrayList<>();
    private boolean superAdmin;
}
