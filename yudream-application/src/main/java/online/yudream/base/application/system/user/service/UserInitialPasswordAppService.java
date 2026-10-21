package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.service.InitialPasswordSetupTokenProvider;
import online.yudream.base.domain.system.user.service.UserRegisterMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 初始密码设置邮件应用服务。
 * <p>
 * 面向管理员（用户管理端）与模板导入调用方：为已知用户生成一次性初始密码设置
 * token 并请求发送邮件；新导入用户密码由服务端生成占位值，不存在可导出的明文密码。
 * 校验口径：邮箱缺失/无效、停用用户、邮箱冲突一律拒绝。
 * 邮件经异步发送器提交，本服务不代表实际送达，也不返回或记录 token。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserInitialPasswordAppService {

    private final UserRepo userRepo;
    private final InitialPasswordSetupTokenProvider initialPasswordSetupTokenProvider;
    private final UserRegisterMailSender userRegisterMailSender;

    /**
     * 为用户签发（或重发）初始密码设置邮件。
     */
    @Transactional
    public void sendInitialPasswordSetupEmail(Long userId) {
        User user = getUser(userId);
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BizException("用户已停用");
        }
        String email = userEmail(user);
        ensureEmailNotTakenByOtherVerifiedUser(user, email);
        String token = initialPasswordSetupTokenProvider.generate(user.getId(), email);
        userRegisterMailSender.sendInitialPasswordSetupEmail(user.getUsername(), email, token);
        log.info("初始密码设置邮件已提交发送: id={}, username={}", user.getId(), user.getUsername());
    }

    private User getUser(Long id) {
        return userRepo.findById(id).orElseThrow(() -> new BizException("用户不存在"));
    }

    private String userEmail(User user) {
        if (user.getEmail() == null || !StringUtils.hasText(user.getEmail().getValue())) {
            throw new BizException("当前账户未设置邮箱");
        }
        return user.getEmail().getValue();
    }

    /** 完成设置时会把邮箱标记为已验证，签发前须确保邮箱未被其他已验证用户占用。 */
    private void ensureEmailNotTakenByOtherVerifiedUser(User user, String email) {
        List<User> sameEmailUsers = userRepo.findByEmailAll(email);
        boolean taken = sameEmailUsers.stream()
                .anyMatch(candidate -> !Objects.equals(candidate.getId(), user.getId()) && candidate.isEmailVerified());
        if (taken) {
            throw new BizException("邮箱已被验证用户使用");
        }
    }
}
