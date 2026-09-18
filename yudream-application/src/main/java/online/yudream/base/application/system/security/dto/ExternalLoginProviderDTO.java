package online.yudream.base.application.system.security.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ExternalLoginProviderDTO {
    private String code;
    private String name;
    private String protocol;
    private String appId;
    private String callbackUrl;
    private String endpoint;
    private boolean enabled;
    private String supportedTypes;
    private String icon;
    private boolean pluginManaged;
    /** 插件登录入口在登录页的呈现方式：ICON（缺省）/ TAB。宿主托管提供方为 null。 */
    private String presentation;
    private int sort;
    private LocalDateTime updateTime;
}
