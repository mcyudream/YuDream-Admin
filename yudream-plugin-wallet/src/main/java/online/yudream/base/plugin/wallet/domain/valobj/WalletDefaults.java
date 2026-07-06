package online.yudream.base.plugin.wallet.domain.valobj;

import online.yudream.base.plugin.wallet.domain.aggregate.WalletAsset;

import java.math.BigDecimal;
import java.util.List;

public final class WalletDefaults {

    public static final String MONEY_ASSET = "CNY";
    public static final String POINT_ASSET = "POINT";

    private WalletDefaults() {
    }

    public static List<WalletAsset> assets() {
        return List.of(
                WalletAsset.create(MONEY_ASSET, "人民币", "￥", 2, true, new BigDecimal("0.01")),
                WalletAsset.create(POINT_ASSET, "积分", "积分", 0, false, BigDecimal.ONE)
        );
    }
}
