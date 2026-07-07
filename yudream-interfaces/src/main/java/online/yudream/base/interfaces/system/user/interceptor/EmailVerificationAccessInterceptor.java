package online.yudream.base.interfaces.system.user.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.service.UserAppService;
import online.yudream.base.domain.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class EmailVerificationAccessInterceptor implements HandlerInterceptor {

    private static final int FORBIDDEN = 403;
    private static final String MESSAGE = "当前账户未验证无法使用其他功能，或者点击重新发送验证邮件";

    private final UserAppService userAppService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!requiresVerifiedEmail(request)) {
            return true;
        }
        Long loginId = currentLoginId();
        if (loginId == null || userAppService.isEmailVerified(loginId)) {
            return true;
        }
        throw new BizException(FORBIDDEN, MESSAGE);
    }

    private boolean requiresVerifiedEmail(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return StringUtils.hasText(path)
                && path.startsWith("/api/")
                && !isAllowedWithoutVerification(request.getMethod(), path);
    }

    private boolean isAllowedWithoutVerification(String method, String path) {
        if (path.startsWith("/api/public/") || path.startsWith("/api/setup/")) {
            return true;
        }
        if (path.equals("/api/system/security/encryption/status")
                || path.equals("/api/system/security/encryption/public-key")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && (path.matches("/api/files/\\d+") || path.matches("/api/files/\\d+/content"))) {
            return true;
        }
        if (path.equals("/api/menu/routes") || path.equals("/api/user/permissions")) {
            return true;
        }
        if (path.equals("/api/user/login")
                || path.equals("/api/user/register")
                || path.equals("/api/user/token/refresh")
                || path.equals("/api/user/verify-email")
                || path.equals("/api/user/passkeys/authentication/options")
                || path.equals("/api/user/passkeys/authentication")) {
            return true;
        }
        if (path.equals("/api/user/me/resend-verification-email")) {
            return true;
        }
        return "GET".equalsIgnoreCase(method)
                && (path.equals("/api/user/me/profile")
                || path.equals("/api/user/me/depts")
                || path.equals("/api/user/me/roles")
                || path.equals("/api/user/me/context"));
    }

    private Long currentLoginId() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId == null ? null : Long.valueOf(String.valueOf(loginId));
        }
        catch (Exception e) {
            return null;
        }
    }
}
