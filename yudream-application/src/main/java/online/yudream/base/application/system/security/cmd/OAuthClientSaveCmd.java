package online.yudream.base.application.system.security.cmd;

import lombok.Data;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthGrantType;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class OAuthClientSaveCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String clientId;
    private String clientName;
    private OAuthClientAuthMethod authMethod;
    private List<OAuthGrantType> grantTypes = new ArrayList<>();
    private List<String> redirectUris = new ArrayList<>();
    private List<String> scopes = new ArrayList<>();
    private long accessTokenTtlSeconds;
    private long refreshTokenTtlSeconds;
    private OAuthRegistrationStatus status;
}
