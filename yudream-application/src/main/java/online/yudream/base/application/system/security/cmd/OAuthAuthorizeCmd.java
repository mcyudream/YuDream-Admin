package online.yudream.base.application.system.security.cmd;

import lombok.Data;

import java.util.List;

@Data
public class OAuthAuthorizeCmd {
    private String responseType;
    private String clientId;
    private String redirectUri;
    private String scope;
    private String state;
    private Long userId;
    /**
     * 当前登录用户的权限码；授权时从中筛出可下发的 plugin:* scope。
     * openid / profile 始终保留。
     */
    private List<String> userPermissions;
}
