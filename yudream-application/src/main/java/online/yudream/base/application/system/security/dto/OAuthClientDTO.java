package online.yudream.base.application.system.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.security.enumerate.OAuthClientAuthMethod;
import online.yudream.base.domain.system.security.enumerate.OAuthGrantType;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthClientDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String clientId;
    private String clientName;
    private OAuthClientAuthMethod authMethod;
    @Builder.Default
    private List<OAuthGrantType> grantTypes = new ArrayList<>();
    @Builder.Default
    private List<String> redirectUris = new ArrayList<>();
    @Builder.Default
    private List<String> scopes = new ArrayList<>();
    private long accessTokenTtlSeconds;
    private long refreshTokenTtlSeconds;
    private OAuthRegistrationStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
