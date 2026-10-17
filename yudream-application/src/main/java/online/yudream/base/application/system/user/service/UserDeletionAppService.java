package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.chat.repo.ChatMessageRepo;
import online.yudream.base.domain.platform.chat.repo.ChatQuotaRepo;
import online.yudream.base.domain.platform.chat.repo.ChatSessionRepo;
import online.yudream.base.domain.platform.mobile.repo.MobileDeviceRepo;
import online.yudream.base.domain.system.monitor.service.OnlineUserGateway;
import online.yudream.base.domain.system.notification.repo.NotificationRepo;
import online.yudream.base.domain.system.security.repo.ApiKeyCredentialRepo;
import online.yudream.base.domain.system.security.repo.ExternalAccountRepo;
import online.yudream.base.domain.system.security.repo.OAuthAccessTokenRepo;
import online.yudream.base.domain.system.security.repo.OAuthAuthorizationCodeRepo;
import online.yudream.base.domain.system.security.repo.PasskeyCredentialRepo;
import online.yudream.base.domain.system.security.repo.RefreshTokenCredentialRepo;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.MessagingIdentityRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.service.UserAuthorityLevels;
import online.yudream.base.domain.system.user.valobj.RoleID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 永久删除用户（purge）：账号记录与账号级联数据一起回收，不可恢复。
 * 与「停用」不同，删除后用户名/邮箱/QQ 等唯一键释放、消息身份解绑、凭据全部失效；
 * 登录日志、接口日志、AI 运行、评论等审计与内容数据保留快照，不作清理。
 */
@Service
@RequiredArgsConstructor
public class UserDeletionAppService {

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final MessagingIdentityRepo messagingIdentityRepo;
    private final PasskeyCredentialRepo passkeyCredentialRepo;
    private final RefreshTokenCredentialRepo refreshTokenCredentialRepo;
    private final OAuthAccessTokenRepo oauthAccessTokenRepo;
    private final OAuthAuthorizationCodeRepo oauthAuthorizationCodeRepo;
    private final ExternalAccountRepo externalAccountRepo;
    private final ApiKeyCredentialRepo apiKeyCredentialRepo;
    private final MobileDeviceRepo mobileDeviceRepo;
    private final NotificationRepo notificationRepo;
    private final ChatMessageRepo chatMessageRepo;
    private final ChatSessionRepo chatSessionRepo;
    private final ChatQuotaRepo chatQuotaRepo;
    private final OnlineUserGateway onlineUserGateway;

    /**
     * 守卫通过后先回收级联数据、再删除账号本身，最后强制该账号全部会话下线
     * （账号不存在后其已发出的令牌不能再继续使用）。
     */
    @Transactional
    public void delete(Long operatorId, Long userId) {
        if (operatorId == null) {
            throw new BizException("无法识别操作者身份");
        }
        if (userId == null) {
            throw new BizException("用户不存在");
        }
        if (operatorId.equals(userId)) {
            throw new BizException("不能删除自己");
        }
        User target = userRepo.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        assertOperatorOutranks(operatorId, target);

        cleanupAccountData(userId);
        userRepo.deleteByIds(List.of(userId));
        onlineUserGateway.kickoutByUserId(userId);
    }

    /**
     * 账号级联清单：消息身份（含 QQ 绑定）、安全凭据（通行密钥/刷新令牌/OAuth/外部账号/API Key）、
     * 移动设备、站内通知、聊天消息与会话及配额。
     */
    private void cleanupAccountData(Long userId) {
        messagingIdentityRepo.deleteByUserId(userId);
        passkeyCredentialRepo.deleteByUserId(userId);
        refreshTokenCredentialRepo.deleteByUserId(userId);
        oauthAccessTokenRepo.deleteByUserId(userId);
        oauthAuthorizationCodeRepo.deleteByUserId(userId);
        externalAccountRepo.deleteByUserId(userId);
        apiKeyCredentialRepo.deleteByUserId(userId);
        mobileDeviceRepo.deleteByUserId(userId);
        notificationRepo.deleteByUserId(userId);
        chatMessageRepo.deleteByUserId(userId);
        chatSessionRepo.deleteByUserId(userId);
        chatQuotaRepo.deleteByUserId(userId);
    }

    /**
     * 删除权限上限：操作者的最高角色权重必须严格高于目标，
     * 否则同级或更低权限账号可删除更高权限账号（越权破坏）。
     */
    private void assertOperatorOutranks(Long operatorId, User target) {
        User operator = userRepo.findById(operatorId).orElseThrow(() -> new BizException("无法识别操作者身份"));
        if (highestWeight(target) >= highestWeight(operator)) {
            throw new BizException("不能删除同级或更高权限的用户");
        }
    }

    private int highestWeight(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return 0;
        }
        List<Long> roleIds = user.getRoles().stream().map(RoleID::getValue).distinct().toList();
        return UserAuthorityLevels.highestWeight(roleRepo.findByIds(roleIds));
    }
}
