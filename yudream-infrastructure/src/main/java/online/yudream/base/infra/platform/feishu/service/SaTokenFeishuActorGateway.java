package online.yudream.base.infra.platform.feishu.service;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.feishu.service.FeishuActorGateway;
import online.yudream.base.domain.system.security.service.ApiKeyAuthenticationContext;
import online.yudream.base.domain.system.security.valobj.ApiKeyAuthentication;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.UserRepo;
import org.springframework.stereotype.Component;

/**
 * 飞书用例认证主体解析：身份只取 Sa-Token 登录态或 API Key 上下文。
 * <p>
 * 与 Wiki 认证主体同一口径：用户必须启用且邮箱已验证，否则按未登录处理（fail closed）。
 * 全局权限码由接口层 {@code @PermissionRegister} 注册并统一鉴权，这里只解析归属用户，
 * 供额度记账使用；前端传入的任何用户标识都不参与归属判断。
 */
@Component
@RequiredArgsConstructor
public class SaTokenFeishuActorGateway implements FeishuActorGateway {

    private final UserRepo userRepo;

    @Override
    public Long currentUserId() {
        ApiKeyAuthentication apiKey = ApiKeyAuthenticationContext.get();
        if (apiKey != null) {
            return apiKey.userId();
        }
        Long userId = loginUserId();
        if (userId == null) {
            return null;
        }
        User user = userRepo.findById(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            return null;
        }
        return userId;
    }

    private Long loginUserId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(loginId));
        }
        catch (NumberFormatException error) {
            return null;
        }
    }
}
