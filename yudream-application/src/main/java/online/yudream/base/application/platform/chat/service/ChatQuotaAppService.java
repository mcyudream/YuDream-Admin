package online.yudream.base.application.platform.chat.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.chat.assembler.ChatAssembler;
import online.yudream.base.application.platform.chat.cmd.ChatQuotaConfigCmd;
import online.yudream.base.application.platform.chat.dto.ChatQuotaDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.chat.aggregate.UserChatQuota;
import online.yudream.base.domain.platform.chat.repo.ChatQuotaRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatQuotaAppService implements online.yudream.base.domain.platform.ai.service.AiUsageQuotaGateway {

    public static final long DEFAULT_DAILY_TOKEN_LIMIT = 200_000L;

    private final CapabilityAppService capabilities;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final ChatQuotaRepo quotaRepo;
    private final online.yudream.base.domain.platform.ai.repo.AiRunRepo runRepo;
    private final java.util.Map<String, online.yudream.base.domain.platform.ai.valobj.AiUsageReservation> liveReservations = new java.util.concurrent.ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public ChatQuotaDTO me(Long userId) {
        ensureEnabled();
        UserChatQuota quota = todayQuota(userId);
        return ChatAssembler.quota(quota);
    }

    @Transactional(readOnly = true)
    public long dailyLimit() {
        ensureEnabled();
        return module().map(ChatQuotaAppService::configuredLimit).orElse(DEFAULT_DAILY_TOKEN_LIMIT);
    }

    @Transactional
    public long updateLimit(ChatQuotaConfigCmd cmd) {
        ensureEnabled();
        if (cmd.getDailyTokenLimit() <= 0) {
            throw new BizException("每日 token 上限必须大于 0");
        }
        CapabilityModule module = module().orElseThrow(() -> new BizException("AI 助手能力配置不存在"));
        Map<String, String> config = new HashMap<>(module.getConfig() == null ? Map.of() : module.getConfig());
        config.put("dailyTokenLimit", String.valueOf(cmd.getDailyTokenLimit()));
        if (cmd.getSystemDailyTokenLimit() != null) {
            if (cmd.getSystemDailyTokenLimit() <= 0) throw new BizException("系统任务每日 token 上限必须大于 0");
            config.put("systemDailyTokenLimit", String.valueOf(cmd.getSystemDailyTokenLimit()));
        }
        module.updateConfig(config);
        capabilityModuleRepo.save(module);
        return cmd.getDailyTokenLimit();
    }

    @Transactional
    public UserChatQuota todayQuota(Long userId) {
        if (userId == null) {
            throw new BizException("当前用户未登录");
        }
        reclaimExpired(userId, quotaDate());
        return quotaRepo.getOrCreate(userId, quotaDate(), dailyLimit());
    }

    @Transactional
    public UserChatQuota recordUsage(Long userId, long totalTokens) {
        UserChatQuota quota = quotaRepo.addUsage(userId, quotaDate(), Math.max(0, totalTokens))
                .orElseThrow(() -> new BizException("今日 token 额度已用完，请明日再试或联系管理员调整额度"));
        return quota;
    }

    @Override
    public online.yudream.base.domain.platform.ai.valobj.AiUsageReservation reserve(Long userId, long tokenBudget) {
        ensureEnabled();
        Long bucket = userId == null ? 0L : userId;
        LocalDate date = quotaDate();
        long limit = userId == null || userId == 0L ? systemDailyLimit() : dailyLimit();
        reclaimExpired(bucket, date);
        UserChatQuota quota = quotaRepo.getOrCreate(bucket, date, limit);
        long amount = Math.min(Math.max(1, tokenBudget), quota.remaining());
        if (amount <= 0) throw new BizException("今日 AI token 额度已用完，请明日再试或联系管理员调整额度");
        String id = java.util.UUID.randomUUID().toString();
        String runId = online.yudream.base.domain.platform.ai.service.HarnessExecutionScope.currentIdempotencyKey();
        if (runId != null && runId.contains(":")) runId = runId.substring(0, runId.indexOf(':'));
        java.time.Instant lease = java.time.Instant.now().plusSeconds(120);
        quotaRepo.reserve(bucket, date, id, amount, runId, lease)
                .orElseThrow(() -> new BizException("AI token 额度已被其他运行占用，请等待当前运行结束"));
        var reservation = new online.yudream.base.domain.platform.ai.valobj.AiUsageReservation(id, bucket, date, amount, runId, lease);
        liveReservations.put(id, reservation);
        return reservation;
    }

    @Override
    public void settle(online.yudream.base.domain.platform.ai.valobj.AiUsageReservation reservation,
                       online.yudream.base.domain.platform.ai.valobj.AiUsage usage) {
        if (reservation == null) return;
        // 不再检查能力状态：关闭能力或跨日后，已发生的调用仍必须核销原账期。
        quotaRepo.settle(reservation.userId(), reservation.usageDate(), reservation.id(),
                reservation.reservedTokens(), usage == null ? 0 : usage.totalTokens());
        liveReservations.remove(reservation.id());
    }

    @Override
    public void renew(String runId) {
        liveReservations.values().stream().filter(reservation -> java.util.Objects.equals(runId, reservation.runId()))
                .forEach(reservation -> quotaRepo.renew(reservation.userId(), reservation.usageDate(), reservation.id(), java.time.Instant.now().plusSeconds(120)));
    }

    @Override
    public void markStarted(online.yudream.base.domain.platform.ai.valobj.AiUsageReservation reservation) {
        if (reservation != null) quotaRepo.markStarted(reservation.userId(), reservation.usageDate(), reservation.id());
    }
    @Override
    public void uncertain(online.yudream.base.domain.platform.ai.valobj.AiUsageReservation reservation) {
        uncertain(reservation, online.yudream.base.domain.platform.ai.valobj.AiUsage.empty());
    }
    @Override
    public void uncertain(online.yudream.base.domain.platform.ai.valobj.AiUsageReservation reservation,
                          online.yudream.base.domain.platform.ai.valobj.AiUsage knownUsage) {
        if (reservation == null) return;
        quotaRepo.uncertain(reservation, knownUsage == null ? online.yudream.base.domain.platform.ai.valobj.AiUsage.empty() : knownUsage);
        liveReservations.remove(reservation.id());
    }

    public java.util.List<online.yudream.base.application.platform.chat.dto.ChatQuotaUncertainDTO> unresolved(Long actorId, String userId, LocalDate date) {
        ensureEnabled();
        Long target = userId == null ? actorId : quotaUserId(userId);
        if (target == null) throw new BizException("当前用户未登录");
        LocalDate accountingDate = date == null ? quotaDate() : date;
        // 查询指定账期时惰性回收它的过期租约，不依赖再次访问当天额度或全库扫描。
        reclaimExpired(target, accountingDate);
        return quotaRepo.unresolved(target, accountingDate).stream()
                .map(reservation -> new online.yudream.base.application.platform.chat.dto.ChatQuotaUncertainDTO(
                        reservation.id(), String.valueOf(reservation.userId()), reservation.usageDate(), reservation.reservedTokens(), reservation.runId(), reservation.knownTokens())).toList();
    }

    /** 管理员根据提供方实际 usage 核对；写零表示已核实没有消费，不是自动猜测。 */
    public void reconcile(online.yudream.base.application.platform.chat.cmd.ChatQuotaReconcileCmd cmd) {
        ensureEnabled();
        Long target = quotaUserId(cmd.userId());
        var usage = cmd.usage();
        if (usage == null || usage.promptTokens() < 0 || usage.completionTokens() < 0 || usage.totalTokens() < 0
                || usage.promptTokens() > Long.MAX_VALUE - usage.completionTokens()
                || usage.totalTokens() != usage.promptTokens() + usage.completionTokens()) throw new BizException("核对用量必须是非负且合计一致的实际 token 数");
        var reservation = quotaRepo.unresolved(target, cmd.usageDate()).stream().filter(item -> item.id().equals(cmd.reservationId()))
                .findFirst().orElseThrow(() -> new BizException("待核对记录不存在或已经核销"));
        if (usage.totalTokens() < reservation.knownTokens()) throw new BizException("最终实际用量不能低于已经确认入账的用量");
        if (quotaRepo.settle(target, cmd.usageDate(), reservation.id(), reservation.reservedTokens(), usage.totalTokens()).isEmpty())
            throw new BizException("记录已经被其他操作核销，请刷新后重试");
    }

    private static Long quotaUserId(String value) {
        try {
            Long parsed = Long.valueOf(value);
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (Exception error) { throw new BizException("额度用户标识无效"); }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30_000)
    public void renewActiveReservations() {
        liveReservations.values().forEach(reservation -> quotaRepo.renew(reservation.userId(), reservation.usageDate(),
                reservation.id(), java.time.Instant.now().plusSeconds(120)));
    }

    private void reclaimExpired(Long userId, LocalDate date) {
        java.time.Instant now = java.time.Instant.now();
        for (var reservation : quotaRepo.reservations(userId, date)) {
            if (liveReservations.containsKey(reservation.id())) continue;
            if (reservation.leaseExpiresAt() != null && reservation.leaseExpiresAt().isAfter(now)) continue;
            var run = reservation.runId() == null ? java.util.Optional.<online.yudream.base.domain.platform.ai.aggregate.AiRun>empty()
                    : runRepo.find(reservation.runId());
            if (run.isPresent() && !run.get().getStatus().terminal() && run.get().getUpdatedAt().isAfter(now.minusSeconds(120))) {
                quotaRepo.renew(userId, date, reservation.id(), now.plusSeconds(120));
            } else {
                // 仅释放未核销预占，不推测模型消费；迟到的真实 usage 由零收费收据补账。
                if (reservation.modelStarted()) quotaRepo.uncertain(reservation);
                else quotaRepo.settle(userId, date, reservation.id(), reservation.reservedTokens(), 0);
            }
        }
    }

    public long systemDailyLimit() {
        ensureEnabled();
        return module().map(module -> configuredPositive(module, "systemDailyTokenLimit", DEFAULT_DAILY_TOKEN_LIMIT))
                .orElse(DEFAULT_DAILY_TOKEN_LIMIT);
    }

    private static LocalDate quotaDate() { return LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")); }

    private static long configuredPositive(CapabilityModule module, String key, long fallback) {
        try {
            long value = Long.parseLong(module.getConfig() == null ? "" : module.getConfig().getOrDefault(key, ""));
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) { return fallback; }
    }

    private void ensureEnabled() {
        capabilities.ensureEnabled("ai", "AI 助手");
    }

    private java.util.Optional<CapabilityModule> module() {
        return capabilityModuleRepo.findByCode("ai");
    }

    private static long configuredLimit(CapabilityModule module) {
        Map<String, String> config = module.getConfig() == null ? Map.of() : module.getConfig();
        String value = config.get("dailyTokenLimit");
        if (value == null || value.isBlank()) {
            return DEFAULT_DAILY_TOKEN_LIMIT;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : DEFAULT_DAILY_TOKEN_LIMIT;
        }
        catch (NumberFormatException ignored) {
            return DEFAULT_DAILY_TOKEN_LIMIT;
        }
    }
}
