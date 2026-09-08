package online.yudream.base.domain.system.user.repo;

import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;
import online.yudream.base.domain.system.user.aggregate.MessagingIdentity;
import online.yudream.base.domain.system.user.enumerate.MessagingIdentityType;

import java.util.List;
import java.util.Optional;

public interface MessagingIdentityRepo {

    MessagingIdentity save(MessagingIdentity identity);

    Optional<MessagingIdentity> findById(Long id);

    List<MessagingIdentity> findByUserId(Long userId);

    List<MessagingIdentity> findByUserIds(List<Long> userIds);

    Optional<MessagingIdentity> findMilkyQq(String identity);

    Optional<MessagingIdentity> findOfficialUserOpenid(String appId, String identity);

    Optional<MessagingIdentity> findOfficialMemberOpenid(String appId, String groupOpenid, String identity);

    List<MessagingIdentity> findByIdentity(String identity);

    Optional<MessagingIdentity> findExact(MilkyConnectionProtocol protocol, MessagingIdentityType identityType,
                                          String appId, String groupOpenid, String identity);

    void deleteById(Long id);

    void deleteMilkyQqByUserId(Long userId);
}
