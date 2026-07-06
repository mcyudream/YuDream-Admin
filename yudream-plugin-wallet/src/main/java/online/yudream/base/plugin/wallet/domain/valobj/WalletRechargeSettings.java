package online.yudream.base.plugin.wallet.domain.valobj;

import java.util.List;

public record WalletRechargeSettings(
        boolean enabled,
        String defaultProductType,
        List<WalletRechargeRule> rules
) {

    public static WalletRechargeSettings defaults(List<String> moneyAssetCodes) {
        return new WalletRechargeSettings(true, "PAGE",
                moneyAssetCodes.stream().map(WalletRechargeRule::defaults).toList());
    }

    public WalletRechargeSettings normalized() {
        return new WalletRechargeSettings(
                enabled,
                defaultProductType == null || defaultProductType.isBlank() ? "PAGE" : defaultProductType.trim().toUpperCase(),
                rules == null ? List.of() : rules.stream().map(WalletRechargeRule::normalized).toList()
        );
    }
}
