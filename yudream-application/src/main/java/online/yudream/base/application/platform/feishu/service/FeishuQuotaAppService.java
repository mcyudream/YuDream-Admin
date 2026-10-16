package online.yudream.base.application.platform.feishu.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.feishu.assembler.FeishuAssembler;
import online.yudream.base.application.platform.feishu.cmd.FeishuQuotaConfigCmd;
import online.yudream.base.application.platform.feishu.dto.FeishuQuotaConfigDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuQuotaDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.feishu.enumerate.FeishuQuotaScope;
import online.yudream.base.domain.platform.feishu.repo.FeishuCallQuotaRepo;
import online.yudream.base.domain.platform.feishu.service.FeishuActorGateway;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaPolicy;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaSnapshot;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 飞书调用额度用例：当前用户额度查询与管理端上限配置。
 * <p>
 * 上限是每用户的上限（分钟/日各一份计数桶），不是角色档位：现有 RBAC 角色没有额度维度，
 * 不新建角色额度模型。能力关闭时用例一律拒绝（应用闸门）。
 */
@Service
@RequiredArgsConstructor
public class FeishuQuotaAppService {

    private final CapabilityAppService capabilities;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final FeishuCallQuotaRepo quotaRepo;
    private final FeishuActorGateway actors;

    public FeishuQuotaDTO me() {
        ensureEnabled();
        Long userId = actors.currentUserId();
        if (userId == null) {
            throw new BizException("当前用户未登录或账号不可用，无法查看飞书调用额度");
        }
        FeishuQuotaPolicy policy = policy();
        FeishuQuotaSnapshot snapshot = quotaRepo.snapshot(userId, FeishuQuotaScope.CLOUD_DOC, Instant.now(), policy);
        return FeishuAssembler.quota(snapshot);
    }

    public FeishuQuotaConfigDTO config() {
        ensureEnabled();
        return FeishuAssembler.quotaConfig(policy());
    }

    public FeishuQuotaConfigDTO updateConfig(FeishuQuotaConfigCmd cmd) {
        ensureEnabled();
        FeishuQuotaPolicy current = policy();
        int perMinuteLimit = cmd.getDefaultPerMinuteLimit() == null ? current.perMinuteLimit() : cmd.getDefaultPerMinuteLimit();
        int dailyLimit = cmd.getDefaultDailyLimit() == null ? current.dailyLimit() : cmd.getDefaultDailyLimit();
        FeishuQuotaPolicy updated = new FeishuQuotaPolicy(perMinuteLimit, dailyLimit);
        CapabilityModule module = module().orElseThrow(() -> new BizException("飞书开放平台能力配置不存在"));
        Map<String, String> config = new HashMap<>(module.getConfig() == null ? Map.of() : module.getConfig());
        config.put(FeishuQuotaPolicy.PER_MINUTE_CONFIG_KEY, String.valueOf(updated.perMinuteLimit()));
        config.put(FeishuQuotaPolicy.DAILY_CONFIG_KEY, String.valueOf(updated.dailyLimit()));
        module.updateConfig(config);
        capabilityModuleRepo.save(module);
        return FeishuAssembler.quotaConfig(updated);
    }

    /** 当前生效的额度策略：能力配置优先，缺键或非法值回落默认。 */
    public FeishuQuotaPolicy policy() {
        return module()
                .map(module -> FeishuQuotaPolicy.fromConfig(module.getConfig()))
                .orElseGet(() -> FeishuQuotaPolicy.of(null, null));
    }

    public void ensureEnabled() {
        capabilities.ensureEnabled("feishu", "飞书开放平台");
    }

    private Optional<CapabilityModule> module() {
        return capabilityModuleRepo.findByCode("feishu");
    }
}
