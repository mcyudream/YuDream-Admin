package online.yudream.base.plugin.spi.system.wallet;

import java.util.List;
import java.util.Optional;

public interface PluginWalletService {

    String MONEY_ASSET_CODE = "CNY";

    List<PluginWalletAsset> assets();

    Optional<PluginWalletAsset> findAsset(String assetCode);

    PluginWalletAsset ensureAsset(PluginWalletAsset asset);

    List<PluginWalletBalance> balances(String userId);

    default List<PluginWalletBalance> listBalances(String assetCode, int page, int size) {
        throw new UnsupportedOperationException("当前钱包插件版本不支持余额列表查询，请更新钱包插件后重试");
    }

    PluginWalletBalance balance(String userId, String assetCode);

    PluginWalletTransaction credit(PluginWalletChangeRequest request);

    PluginWalletTransaction debit(PluginWalletChangeRequest request);

    PluginWalletTransaction transfer(PluginWalletTransferRequest request);

    default List<PluginWalletTransaction> transactions(PluginWalletTransactionQuery query) {
        throw new UnsupportedOperationException("当前钱包插件版本不支持交易记录查询，请更新钱包插件后重试");
    }

    Optional<PluginWalletTransaction> findTransactionByBusinessNo(String businessNo);
}
