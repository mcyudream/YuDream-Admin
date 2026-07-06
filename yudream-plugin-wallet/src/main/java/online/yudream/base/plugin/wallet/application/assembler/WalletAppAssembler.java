package online.yudream.base.plugin.wallet.application.assembler;

import online.yudream.base.plugin.spi.system.wallet.PluginWalletAsset;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletBalance;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletTransaction;
import online.yudream.base.plugin.wallet.domain.aggregate.WalletAsset;
import online.yudream.base.plugin.wallet.domain.aggregate.WalletBalance;
import online.yudream.base.plugin.wallet.domain.aggregate.WalletTransaction;

public class WalletAppAssembler {

    public PluginWalletAsset toSpi(WalletAsset asset) {
        return new PluginWalletAsset(asset.code(), asset.name(), asset.symbol(), asset.scale(), asset.money(),
                asset.enabled(), asset.transferEnabled(), asset.minTransferAmount());
    }

    public PluginWalletBalance toSpi(WalletBalance balance) {
        return new PluginWalletBalance(balance.userId(), balance.assetCode(), balance.balance(), balance.updatedAt());
    }

    public PluginWalletTransaction toSpi(WalletTransaction transaction) {
        return new PluginWalletTransaction(
                transaction.id(),
                transaction.businessNo(),
                transaction.type().name(),
                transaction.source(),
                transaction.assetCode(),
                transaction.fromUserId(),
                transaction.toUserId(),
                transaction.amount(),
                transaction.fromBalanceAfter(),
                transaction.toBalanceAfter(),
                transaction.remark(),
                transaction.createdAt()
        );
    }
}
