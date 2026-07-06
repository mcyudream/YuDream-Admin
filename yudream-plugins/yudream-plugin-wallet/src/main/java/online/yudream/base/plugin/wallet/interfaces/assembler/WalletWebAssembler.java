package online.yudream.base.plugin.wallet.interfaces.assembler;

import online.yudream.base.plugin.spi.system.wallet.PluginWalletAsset;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletBalance;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletTransaction;
import online.yudream.base.plugin.wallet.application.cmd.WalletAssetSaveCmd;
import online.yudream.base.plugin.wallet.application.cmd.WalletChangeCmd;
import online.yudream.base.plugin.wallet.application.cmd.WalletRechargeCreateCmd;
import online.yudream.base.plugin.wallet.application.cmd.WalletRechargeSettingsSaveCmd;
import online.yudream.base.plugin.wallet.application.cmd.WalletTransferCmd;
import online.yudream.base.plugin.wallet.interfaces.request.WalletAssetSaveRequest;
import online.yudream.base.plugin.wallet.interfaces.request.WalletChangeRequest;
import online.yudream.base.plugin.wallet.interfaces.request.WalletRechargeCreateRequest;
import online.yudream.base.plugin.wallet.interfaces.request.WalletRechargeSettingsSaveRequest;
import online.yudream.base.plugin.wallet.interfaces.request.WalletTransferRequest;
import online.yudream.base.plugin.wallet.interfaces.res.WalletAssetRes;
import online.yudream.base.plugin.wallet.interfaces.res.WalletBalanceRes;
import online.yudream.base.plugin.wallet.interfaces.res.WalletTransactionRes;
import online.yudream.base.plugin.wallet.interfaces.res.WalletUserRes;

public class WalletWebAssembler {

    public WalletAssetSaveCmd toCmd(WalletAssetSaveRequest request) {
        return new WalletAssetSaveCmd(request.code(), request.name(), request.symbol(), request.scale(),
                request.money(), request.enabled(), request.transferEnabled(), request.minTransferAmount());
    }

    public WalletChangeCmd toCmd(WalletChangeRequest request) {
        return new WalletChangeCmd(request.userId(), request.assetCode(), request.amount(),
                request.businessNo(), request.remark());
    }

    public WalletTransferCmd toCmd(WalletTransferRequest request) {
        return new WalletTransferCmd(request.fromUserId(), request.toUserId(), request.assetCode(),
                request.amount(), request.businessNo(), request.remark());
    }

    public WalletRechargeCreateCmd toCmd(WalletRechargeCreateRequest request) {
        return new WalletRechargeCreateCmd(request.userId(), request.assetCode(), request.channelCode(),
                request.payAmount(), request.productType(), request.remark());
    }

    public WalletRechargeSettingsSaveCmd toCmd(WalletRechargeSettingsSaveRequest request) {
        return new WalletRechargeSettingsSaveCmd(
                request.enabled(),
                request.defaultProductType(),
                request.rules() == null ? java.util.List.of() : request.rules().stream()
                        .map(rule -> new WalletRechargeSettingsSaveCmd.Rule(
                                rule.assetCode(),
                                rule.enabled(),
                                rule.ratio(),
                                rule.minPayAmount(),
                                rule.maxPayAmount()
                        ))
                        .toList()
        );
    }

    public WalletAssetRes toRes(PluginWalletAsset asset) {
        return new WalletAssetRes(asset.code(), asset.name(), asset.symbol(), asset.scale(), asset.money(),
                asset.enabled(), asset.transferEnabled(), asset.minTransferAmount());
    }

    public WalletBalanceRes toRes(PluginWalletBalance balance) {
        return toRes(balance, null);
    }

    public WalletBalanceRes toRes(PluginWalletBalance balance, WalletUserRes user) {
        return new WalletBalanceRes(balance.userId(), user, balance.assetCode(), balance.balance(), balance.updatedAt());
    }

    public WalletTransactionRes toRes(PluginWalletTransaction transaction) {
        return toRes(transaction, null, null);
    }

    public WalletTransactionRes toRes(PluginWalletTransaction transaction, WalletUserRes fromUser, WalletUserRes toUser) {
        return new WalletTransactionRes(
                transaction.id(),
                transaction.businessNo(),
                transaction.type(),
                transaction.source(),
                transaction.assetCode(),
                transaction.fromUserId(),
                fromUser,
                transaction.toUserId(),
                toUser,
                direction(transaction),
                transaction.amount(),
                transaction.fromBalanceAfter(),
                transaction.toBalanceAfter(),
                transaction.remark(),
                transaction.createdAt()
        );
    }

    private String direction(PluginWalletTransaction transaction) {
        if ("CREDIT".equals(transaction.type())) {
            return "IN";
        }
        if ("DEBIT".equals(transaction.type())) {
            return "OUT";
        }
        return "TRANSFER";
    }
}
