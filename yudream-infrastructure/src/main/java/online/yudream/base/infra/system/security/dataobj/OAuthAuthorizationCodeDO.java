package online.yudream.base.infra.system.security.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.security.enumerate.OAuthAuthorizationCodeStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "oauthAuthorizationCode")
public class OAuthAuthorizationCodeDO extends BaseDO {
    @Indexed(unique = true)
    private String code;
    private String clientId;
    private Long userId;
    private String redirectUri;
    private List<String> scopes;
    private String state;
    private LocalDateTime expireTime;
    private LocalDateTime usedTime;
    private OAuthAuthorizationCodeStatus status;
}
