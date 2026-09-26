package online.yudream.base.interfaces.system.backup.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 异地备份目标响应（不回传密码）。 */
@Data
@Builder
public class RemoteTargetRes {
    private String id;
    private String code;
    private String name;
    private String type;
    private String host;
    private Integer port;
    private String username;
    private String basePath;
    private Boolean passiveMode;
    private Boolean insecureTls;
    private Boolean enabled;
    private Boolean passwordSet;
    private LocalDateTime createTime;
}
