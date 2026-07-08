package online.yudream.base.plugin.wallet.interfaces.controller;

import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.wallet.bootstrap.WalletPlugin;
import online.yudream.base.plugin.wallet.interfaces.http.WalletHttpFacade;

public class WalletController {

    private final WalletHttpFacade http;

    public WalletController(WalletHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/status", permission = WalletPlugin.VIEW_PERMISSION)
    public PluginHttpResponse status() {
        return http.status();
    }

    @PluginHttpEndpoint(method = "GET", path = "/assets", permission = WalletPlugin.VIEW_PERMISSION)
    public PluginHttpResponse assets(PluginHttpRequest request) {
        return http.assets(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/assets", permission = WalletPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse saveAsset(PluginHttpRequest request) {
        return http.saveAsset(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/assets/{assetCode}", permission = WalletPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deleteAsset(PluginHttpRequest request) {
        return http.deleteAsset(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/recharge/options", permission = WalletPlugin.USER_PERMISSION)
    public PluginHttpResponse rechargeOptions() {
        return http.rechargeOptions();
    }

    @PluginHttpEndpoint(method = "POST", path = "/recharges", permission = WalletPlugin.USER_PERMISSION)
    public PluginHttpResponse createRecharge(PluginHttpRequest request) {
        return http.createRecharge(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/recharge/settings", permission = WalletPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse rechargeSettings() {
        return http.rechargeSettings();
    }

    @PluginHttpEndpoint(method = "PUT", path = "/recharge/settings", permission = WalletPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse saveRechargeSettings(PluginHttpRequest request) {
        return http.saveRechargeSettings(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/balances", permission = WalletPlugin.USER_PERMISSION)
    public PluginHttpResponse balances(PluginHttpRequest request) {
        return http.balances(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/balances", permission = WalletPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse adminBalances(PluginHttpRequest request) {
        return http.adminBalances(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/users/{userId}/balances", permission = WalletPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse userBalances(PluginHttpRequest request) {
        return http.userBalances(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/users/{userId}/balances/{assetCode}", permission = WalletPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse userBalance(PluginHttpRequest request) {
        return http.userBalance(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/game/balance", permission = WalletPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse gameBalance(PluginHttpRequest request) {
        return http.gameBalance(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/balances/credit", permission = WalletPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse credit(PluginHttpRequest request) {
        return http.credit(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/balances/debit", permission = WalletPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse debit(PluginHttpRequest request) {
        return http.debit(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/game/credit", permission = WalletPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse gameCredit(PluginHttpRequest request) {
        return http.gameCredit(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/game/debit", permission = WalletPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse gameDebit(PluginHttpRequest request) {
        return http.gameDebit(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/balances/transfer", permission = WalletPlugin.USER_PERMISSION)
    public PluginHttpResponse transfer(PluginHttpRequest request) {
        return http.transfer(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/transactions", permission = WalletPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse transactions(PluginHttpRequest request) {
        return http.transactions(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/my/transactions", permission = WalletPlugin.USER_PERMISSION)
    public PluginHttpResponse myTransactions(PluginHttpRequest request) {
        return http.myTransactions(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/transactions/by-business-no", permission = WalletPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse transactionByBusinessNo(PluginHttpRequest request) {
        return http.transactionByBusinessNo(request);
    }
}
