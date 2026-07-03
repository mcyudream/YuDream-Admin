package online.yudream.base.interfaces.system.security.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;

import java.util.ArrayList;
import java.util.List;

@Data
public class OAuthProviderSaveRequest {

    @NotBlank(message = "OAuth 提供商编码不能为空")
    private String code;

    @NotBlank(message = "OAuth 提供商名称不能为空")
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
