package online.yudream.base.application.system.security.cmd;

import lombok.Data;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class OAuthProviderSaveCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String name;
    private String issuerUri;
    private String authorizationUri;
    private String tokenUri;
    private String userInfoUri;
    private String clientId;
    private String clientSecret;
    private OAuthClientAuthMethod authMethod;
    private List<String> scopes = new ArrayList<>();
    private String redirectUri;
    private OAuthRegistrationStatus status;
}
