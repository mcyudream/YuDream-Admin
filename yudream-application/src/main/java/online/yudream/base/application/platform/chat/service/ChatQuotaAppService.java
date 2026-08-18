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
public class ChatQuotaAppService {

    public static final long DEFAULT_DAILY_TOKEN_LIMIT = 200_000L;

    private final CapabilityAppService capabilities;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final ChatQuotaRepo quotaRepo;

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
        module.updateConfig(config);
        capabilityModuleRepo.save(module);
        return cmd.getDailyTokenLimit();
    }

    @Transactional
    public UserChatQuota todayQuota(Long userId) {
        if (userId == null) {
            throw new BizException("当前用户未登录");
        }
        return quotaRepo.getOrCreate(userId, LocalDate.now(), dailyLimit());
    }

    @Transactional
    public UserChatQuota recordUsage(Long userId, long totalTokens) {
        UserChatQuota quota = quotaRepo.addUsage(userId, LocalDate.now(), Math.max(0, totalTokens))
                .orElseThrow(() -> new BizException("今日 token 额度已用完，请明日再试或联系管理员调整额度"));
        return quota;
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
