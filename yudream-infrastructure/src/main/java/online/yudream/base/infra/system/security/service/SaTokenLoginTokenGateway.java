package online.yudream.base.infra.system.security.service;

import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import online.yudream.base.domain.system.security.service.LoginTokenGateway;
import org.springframework.stereotype.Service;

@Service
public class SaTokenLoginTokenGateway implements LoginTokenGateway {

    @Override
    public String issueAccessToken(Long userId, Long timeoutSeconds) {
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            StpUtil.login(userId, new SaLoginModel().setTimeout(timeoutSeconds));
        } else {
            StpUtil.login(userId);
        }
        return StpUtil.getTokenValue();
    }

    @Override
    public String tokenName() {
        return StpUtil.getTokenName();
    }
}
