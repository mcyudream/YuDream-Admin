package online.yudream.base.infra.system.security.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.security.enumerate.CredentialStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "oauthAccessToken")
public class OAuthAccessTokenDO extends BaseDO {
    @Indexed
    private String accessTokenHash;
    @Indexed
    private String refreshTokenHash;
    private String clientId;
    private Long userId;
    private List<String> scopes;
    private LocalDateTime accessExpireTime;
    private LocalDateTime refreshExpireTime;
    private CredentialStatus status;
}
