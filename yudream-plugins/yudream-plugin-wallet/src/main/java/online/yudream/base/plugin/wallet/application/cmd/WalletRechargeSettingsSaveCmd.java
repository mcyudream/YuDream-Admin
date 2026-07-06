package online.yudream.base.plugin.wallet.application.cmd;

import java.math.BigDecimal;
import java.util.List;

public record WalletRechargeSettingsSaveCmd(
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
