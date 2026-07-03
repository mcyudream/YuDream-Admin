package online.yudream.base.domain.system.security.valobj;

import online.yudream.base.domain.common.exception.BizException;

public record TokenPolicy(
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds,
        boolean refreshRotationEnabled
) {

    public TokenPolicy {
        if (accessTokenTtlSeconds <= 0) {
            throw new BizException("访问令牌有效期必须大于0");
        }
        if (refreshTokenTtlSeconds <= 0) {
            throw new BizException("刷新令牌有效期必须大于0");
        }
        if (refreshTokenTtlSeconds < accessTokenTtlSeconds) {
            throw new BizException("刷新令牌有效期不能小于访问令牌有效期");
        }
    }

    public static TokenPolicy defaultPolicy() {
        return new TokenPolicy(7200, 604800, true);
    }
}
