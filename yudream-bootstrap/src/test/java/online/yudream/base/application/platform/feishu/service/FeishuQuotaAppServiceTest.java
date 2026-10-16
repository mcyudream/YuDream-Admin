package online.yudream.base.application.platform.feishu.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 额度用例：上限读取回落、管理端部分更新、身份与能力闸门。
 */
class FeishuQuotaAppServiceTest {

    private final CapabilityAppService capabilities = mock(CapabilityAppService.class);
    private final CapabilityModuleRepo moduleRepo = mock(CapabilityModuleRepo.class);
    private final FeishuCallQuotaRepo quotaRepo = mock(FeishuCallQuotaRepo.class);
    private final FeishuActorGateway actors = mock(FeishuActorGateway.class);
    private final FeishuQuotaAppService service = new FeishuQuotaAppService(capabilities, moduleRepo, quotaRepo, actors);

    @BeforeEach
    void stubModule() {
        lenient().when(moduleRepo.findByCode("feishu")).thenReturn(Optional.of(module(Map.of(
                FeishuQuotaPolicy.PER_MINUTE_CONFIG_KEY, "30"))));
        lenient().when(actors.currentUserId()).thenReturn(7L);
    }

    @Test
    void policyReadsConfiguredValueAndFallsBackForMissingKeys() {
        FeishuQuotaPolicy policy = service.policy();
        assertThat(policy.perMinuteLimit()).isEqualTo(30);
        assertThat(policy.dailyLimit()).isEqualTo(FeishuQuotaPolicy.DEFAULT_DAILY_LIMIT);
    }

    @Test
    void policyFallsBackToDefaultsWhenModuleMissing() {
        when(moduleRepo.findByCode("feishu")).thenReturn(Optional.empty());
        FeishuQuotaPolicy policy = service.policy();
        assertThat(policy.perMinuteLimit()).isEqualTo(20);
        assertThat(policy.dailyLimit()).isEqualTo(500);
    }

    @Test
    void configReturnsEffectiveLimits() {
        FeishuQuotaConfigDTO config = service.config();
        assertThat(config.getDefaultPerMinuteLimit()).isEqualTo(30);
        assertThat(config.getDefaultDailyLimit()).isEqualTo(500);
    }

    @Test
    void updateConfigKeepsUnspecifiedValuesAndPersistsBothKeys() {
        FeishuQuotaConfigDTO updated = service.updateConfig(FeishuQuotaConfigCmd.builder()
                .defaultDailyLimit(900)
                .build());

        assertThat(updated.getDefaultPerMinuteLimit()).isEqualTo(30);
        assertThat(updated.getDefaultDailyLimit()).isEqualTo(900);
        ArgumentCaptor<CapabilityModule> saved = ArgumentCaptor.forClass(CapabilityModule.class);
        verify(moduleRepo).save(saved.capture());
        assertThat(saved.getValue().getConfig())
                .containsEntry(FeishuQuotaPolicy.PER_MINUTE_CONFIG_KEY, "30")
                .containsEntry(FeishuQuotaPolicy.DAILY_CONFIG_KEY, "900");
    }

    @Test
    void updateConfigRejectsNonPositiveLimits() {
        assertThatThrownBy(() -> service.updateConfig(FeishuQuotaConfigCmd.builder().defaultPerMinuteLimit(0).build()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("每分钟调用上限必须大于 0");
        assertThatThrownBy(() -> service.updateConfig(FeishuQuotaConfigCmd.builder().defaultDailyLimit(-3).build()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("每日调用上限必须大于 0");
        verify(moduleRepo, never()).save(any());
    }

    @Test
    void rejectsWhenCapabilityDisabled() {
        doThrow(new BizException("飞书开放平台能力未启用，请先在平台能力中启用")).when(capabilities)
                .ensureEnabled("feishu", "飞书开放平台");

        assertThatThrownBy(() -> service.config()).isInstanceOf(BizException.class).hasMessageContaining("能力未启用");
        assertThatThrownBy(service::me).isInstanceOf(BizException.class).hasMessageContaining("能力未启用");
        verify(moduleRepo, never()).save(any());
    }

    @Test
    void rejectsQuotaViewWhenUnauthenticated() {
        when(actors.currentUserId()).thenReturn(null);
        assertThatThrownBy(service::me).isInstanceOf(BizException.class).hasMessageContaining("未登录");
        verify(quotaRepo, never()).snapshot(any(), any(), any(), any());
    }

    @Test
    void meReturnsCurrentUserSnapshotWithStringUserId() {
        when(quotaRepo.snapshot(eq(7L), eq(FeishuQuotaScope.CLOUD_DOC), any(Instant.class), any(FeishuQuotaPolicy.class)))
                .thenReturn(new FeishuQuotaSnapshot(7L, FeishuQuotaScope.CLOUD_DOC,
                        "202610161100", 3, 30, LocalDate.now().toString().replace("-", ""), 12, 500));

        FeishuQuotaDTO dto = service.me();

        assertThat(dto.getUserId()).isEqualTo("7");
        assertThat(dto.getMinuteUsed()).isEqualTo(3);
        assertThat(dto.getMinuteLimit()).isEqualTo(30);
        assertThat(dto.getDailyUsed()).isEqualTo(12);
        assertThat(dto.getDailyLimit()).isEqualTo(500);
    }

    private static CapabilityModule module(Map<String, String> config) {
        return CapabilityModule.builder()
                .code("feishu")
                .name("飞书开放平台")
                .enabled(true)
                .config(new HashMap<>(config))
                .build();
    }
}
