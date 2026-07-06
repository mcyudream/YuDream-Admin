package online.yudream.base.plugin.wallet.application.dto;

import java.util.List;

public record WalletPaymentChannelDTO(
        String code,
        String name,
        String icon,
        String description,
        boolean enabled,
        List<String> productTypes
) {
}
