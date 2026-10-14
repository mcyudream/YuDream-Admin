package online.yudream.base.application.platform.chat.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.chat.aggregate.UserChatQuota;
import online.yudream.base.domain.platform.chat.repo.ChatQuotaRepo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ChatQuotaAppServiceTest {

    @Test
    void recordUsageUsesAtomicRepoIncrement() {
        CapabilityAppService capabilities = mock(CapabilityAppService.class);
        CapabilityModuleRepo modules = mock(CapabilityModuleRepo.class);
        ChatQuotaRepo quotas = mock(ChatQuotaRepo.class);
        CapabilityModule module = new CapabilityModule();
        module.setConfig(Map.of("dailyTokenLimit", "500"));
        LocalDate day = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        when(modules.findByCode("ai")).thenReturn(Optional.of(module));
        when(quotas.addUsage(1L, day, 30)).thenReturn(
                Optional.of(UserChatQuota.of(1L, day, 500)));
        ChatQuotaAppService service = new ChatQuotaAppService(capabilities, modules, quotas,
                mock(online.yudream.base.domain.platform.ai.repo.AiRunRepo.class));

        UserChatQuota quota = service.recordUsage(1L, 30);

        assertThat(quota.getLimitTokens()).isEqualTo(500);
        verify(quotas).addUsage(1L, day, 30);
    }
}
