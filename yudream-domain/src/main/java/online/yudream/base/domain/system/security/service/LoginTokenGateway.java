package online.yudream.base.domain.system.security.service;

public interface LoginTokenGateway {

    String issueAccessToken(Long userId, Long timeoutSeconds);

    String tokenName();
}
