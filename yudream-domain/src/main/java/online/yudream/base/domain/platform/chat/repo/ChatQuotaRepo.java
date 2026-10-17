package online.yudream.base.domain.platform.chat.repo;

import online.yudream.base.domain.platform.chat.aggregate.UserChatQuota;

import java.time.LocalDate;
import java.util.Optional;

public interface ChatQuotaRepo {

    UserChatQuota save(UserChatQuota quota);

    Optional<UserChatQuota> findByUserAndDate(Long userId, LocalDate usageDate);

    UserChatQuota getOrCreate(Long userId, LocalDate usageDate, long limitTokens);

    Optional<UserChatQuota> addUsage(Long userId, LocalDate usageDate, long tokens);

    Optional<UserChatQuota> reserve(Long userId, LocalDate usageDate, String reservationId, long tokens,
                                    String runId, java.time.Instant leaseExpiresAt);
    java.util.List<online.yudream.base.domain.platform.ai.valobj.AiUsageReservation> reservations(Long userId, LocalDate usageDate);
    void renew(Long userId, LocalDate usageDate, String reservationId, java.time.Instant leaseExpiresAt);
    void markStarted(Long userId, LocalDate usageDate, String reservationId);
    void uncertain(online.yudream.base.domain.platform.ai.valobj.AiUsageReservation reservation);
    default void uncertain(online.yudream.base.domain.platform.ai.valobj.AiUsageReservation reservation,
                           online.yudream.base.domain.platform.ai.valobj.AiUsage knownUsage) { uncertain(reservation); }
    java.util.List<online.yudream.base.domain.platform.ai.valobj.AiUsageReservation> unresolved(Long userId, LocalDate usageDate);

    /** 按预占标识核销一次；真实已发生用量不得因超额而丢失。 */
    Optional<UserChatQuota> settle(Long userId, LocalDate usageDate, String reservationId, long reservedTokens, long usedTokens);

    /** 删除该用户的全部配额记录（账号删除级联）。 */
    void deleteByUserId(Long userId);
}
