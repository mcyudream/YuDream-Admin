package online.yudream.base.domain.platform.chat.repo;

import online.yudream.base.domain.platform.chat.aggregate.UserChatQuota;

import java.time.LocalDate;
import java.util.Optional;

public interface ChatQuotaRepo {

    UserChatQuota save(UserChatQuota quota);

    Optional<UserChatQuota> findByUserAndDate(Long userId, LocalDate usageDate);

    UserChatQuota getOrCreate(Long userId, LocalDate usageDate, long limitTokens);

    Optional<UserChatQuota> addUsage(Long userId, LocalDate usageDate, long tokens);
}
