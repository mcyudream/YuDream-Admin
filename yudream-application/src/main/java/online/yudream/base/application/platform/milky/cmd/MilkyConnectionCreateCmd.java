package online.yudream.base.application.platform.milky.cmd;

import lombok.Data;

@Data
public class MilkyConnectionCreateCmd {
    private String name;
    private String protocol;
    private String baseUrl;
    private String token;
    private String appId;
    private String appSecret;
    private Boolean sandbox;
    private Integer intents;
    private String commandMenuImageMode;
    private String commandMenuPublicBaseUrl;
}
