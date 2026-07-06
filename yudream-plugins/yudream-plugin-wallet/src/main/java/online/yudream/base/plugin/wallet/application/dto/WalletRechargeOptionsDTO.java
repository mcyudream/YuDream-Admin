package online.yudream.base.plugin.wallet.application.dto;

import online.yudream.base.plugin.spi.system.wallet.PluginWalletAsset;

import java.util.List;

public record WalletRechargeOptionsDTO(
        boolean enabled,
        String defaultProductType,
        List<WalletPaymentChannelDTO> channels,
        List<PluginWalletAsset> assets,
        List<WalletRechargeRuleDTO> rules
) {
}
