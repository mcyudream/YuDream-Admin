package online.yudream.base.plugin.wallet.interfaces.request;

import java.math.BigDecimal;
import java.util.List;

public record WalletRechargeSettingsSaveRequest(
        Boolean enabled,
        String defaultProductType,
        List<Rule> rules
) {
    public record Rule(
            String assetCode,
            Boolean enabled,
            BigDecimal ratio,
            BigDecimal minPayAmount,
            BigDecimal maxPayAmount
    ) {
    }
}
