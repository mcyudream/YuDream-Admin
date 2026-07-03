package online.yudream.base.infra.system.security.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthGrantType;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sysOAuthClientRegistration")
public class OAuthClientRegistrationDO extends BaseDO {

    @Indexed(unique = true)
    private String clientId;
    private String clientName;
    private String clientSecretHash;
    private OAuthClientAuthMethod authMethod;
    private List<OAuthGrantType> grantTypes = new ArrayList<>();
    private List<String> redirectUris = new ArrayList<>();
    private List<String> scopes = new ArrayList<>();
    private long accessTokenTtlSeconds;
    private long refreshTokenTtlSeconds;
    private OAuthRegistrationStatus status;
}
