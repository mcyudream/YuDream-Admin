package online.yudream.base.interfaces.system.security.request;

import lombok.Data;

@Data
public class OAuthTokenRequest {
    private String grantType;
    private String clientId;
    private String clientSecret;
    private String code;
    private String redirectUri;
    private String refreshToken;

    public void setGrant_type(String grantType) {
        this.grantType = grantType;
    }

    public void setClient_id(String clientId) {
        this.clientId = clientId;
    }

    public void setClient_secret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public void setRedirect_uri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public void setRefresh_token(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
