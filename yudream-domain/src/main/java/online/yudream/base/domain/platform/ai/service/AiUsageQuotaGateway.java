package online.yudream.base.domain.platform.ai.service;

import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.ai.valobj.AiUsageReservation;

/** 所有宿主与插件模型调用共用的用量预占/结算端口。 */
public interface AiUsageQuotaGateway {
    AiUsageReservation reserve(Long userId, long tokenBudget);
    void settle(AiUsageReservation reservation, AiUsage usage);
    default void release(AiUsageReservation reservation) { settle(reservation, AiUsage.empty()); }
    default void renew(String runId) { }
    default void markStarted(AiUsageReservation reservation) { }
    /** 有模型请求但没有可核实 usage：保留待核对预算，不捏造实际消费。 */
    default void uncertain(AiUsageReservation reservation) { throw new UnsupportedOperationException("额度实现不支持未知用量核对"); }
    /** 先记可核实的部分消费，余下预算继续保留；最终核对只补差额。 */
    default void uncertain(AiUsageReservation reservation, AiUsage knownUsage) { uncertain(reservation); }
}
