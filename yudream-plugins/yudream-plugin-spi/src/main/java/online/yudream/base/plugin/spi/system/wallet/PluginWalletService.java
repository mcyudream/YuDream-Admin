package online.yudream.base.plugin.spi.system.wallet;

import java.util.List;
import java.util.Optional;

public interface PluginWalletService {

    String MONEY_ASSET_CODE = "CNY";

    List<PluginWalletAsset> assets();

    Optional<PluginWalletAsset> findAsset(String assetCode);

    PluginWalletAsset ensureAsset(PluginWalletAsset asset);

    List<PluginWalletBalance> balances(String userId);

    PluginWalletBalance balance(String userId, String assetCode);

    PluginWalletTransaction credit(PluginWalletChangeRequest request);

    PluginWalletTransaction debit(PluginWalletChangeRequest request);

    PluginWalletTransaction transfer(PluginWalletTransferRequest request);

    Optional<PluginWalletTransaction> findTransactionByBusinessNo(String businessNo);
}
