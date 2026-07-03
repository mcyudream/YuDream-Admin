package online.yudream.base.interfaces.system.security.request;

import lombok.Data;

@Data
public class OAuthAuthorizeRequest {
    private String responseType;
    private String clientId;
    private String redirectUri;
    private String scope;
    private String state;

    public void setResponse_type(String responseType) {
        this.responseType = responseType;
    }

    public void setClient_id(String clientId) {
        this.clientId = clientId;
    }

    public void setRedirect_uri(String redirectUri) {
        this.redirectUri = redirectUri;
    }
}
