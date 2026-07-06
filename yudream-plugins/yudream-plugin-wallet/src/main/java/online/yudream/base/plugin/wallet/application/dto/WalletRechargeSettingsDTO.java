package online.yudream.base.plugin.wallet.application.dto;

import java.util.List;

public record WalletRechargeSettingsDTO(
        boolean enabled,
        String defaultProductType,
        List<WalletRechargeRuleDTO> rules
) {
}
