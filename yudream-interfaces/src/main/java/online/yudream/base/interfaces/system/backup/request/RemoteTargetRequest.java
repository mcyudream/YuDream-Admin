package online.yudream.base.interfaces.system.backup.request;

import lombok.Data;

/** 创建/更新异地备份目标请求。 */
@Data
public class RemoteTargetRequest {
    private String code;
    private String name;
    /** FTP / FTPS / WEBDAV。 */
    private String type;
    private String host;
    private Integer port;
    private String username;
    /** 更新时留空表示保持原密码。 */
    private String password;
    private String basePath;
    private Boolean passiveMode;
}
