package online.yudream.base.interfaces.system.security.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthGrantType;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;

import java.util.ArrayList;
import java.util.List;

@Data
public class OAuthClientSaveRequest {

    @NotBlank(message = "OAuth 客户端ID不能为空")
    private String clientId;

    @NotBlank(message = "OAuth 客户端名称不能为空")
    private String clientName;

    private OAuthClientAuthMethod authMethod;
    private List<OAuthGrantType> grantTypes = new ArrayList<>();
    private List<String> redirectUris = new ArrayList<>();
    private List<String> scopes = new ArrayList<>();
    private long accessTokenTtlSeconds = 7200;
    private long refreshTokenTtlSeconds = 604800;
    private OAuthRegistrationStatus status;
}
