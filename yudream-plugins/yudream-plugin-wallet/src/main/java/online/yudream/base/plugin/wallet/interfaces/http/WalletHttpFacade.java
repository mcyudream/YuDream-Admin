package online.yudream.base.plugin.wallet.interfaces.http;

import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.wallet.bootstrap.WalletPlugin;
import online.yudream.base.plugin.wallet.application.service.WalletAppService;
import online.yudream.base.plugin.wallet.application.cmd.WalletTransferCmd;
import online.yudream.base.plugin.wallet.infrastructure.support.JsonSupport;
import online.yudream.base.plugin.wallet.interfaces.assembler.WalletWebAssembler;
import online.yudream.base.plugin.wallet.interfaces.request.WalletAssetSaveRequest;
import online.yudream.base.plugin.wallet.interfaces.request.WalletChangeRequest;
import online.yudream.base.plugin.wallet.interfaces.request.WalletRechargeCreateRequest;
import online.yudream.base.plugin.wallet.interfaces.request.WalletRechargeSettingsSaveRequest;
import online.yudream.base.plugin.wallet.interfaces.request.WalletTransferRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WalletHttpFacade {

    private final WalletAppService appService;
    private final FrameworkServices framework;
    private final WalletWebAssembler assembler = new WalletWebAssembler();

    public WalletHttpFacade(WalletAppService appService, FrameworkServices framework) {
        this.appService = appService;
        this.framework = framework;
    }

    public PluginHttpResponse status() {
        return PluginHttpResponse.ok(appService.summary());
    }

    public PluginHttpResponse assets(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.listAssets(page(request), size(request)).stream()
                .map(assembler::toRes)
                .toList());
    }

    public PluginHttpResponse saveAsset(PluginHttpRequest request) {
        WalletAssetSaveRequest body = JsonSupport.read(request.body(), WalletAssetSaveRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.saveAsset(assembler.toCmd(body))));
    }

    public PluginHttpResponse deleteAsset(PluginHttpRequest request) {
        appService.deleteAsset(pathSegment(request.path(), 1));
        return PluginHttpResponse.ok(Map.of("deleted", true));
    }

    public PluginHttpResponse rechargeOptions() {
        return PluginHttpResponse.ok(appService.rechargeOptions());
    }

    public PluginHttpResponse rechargeSettings() {
        return PluginHttpResponse.ok(appService.rechargeSettings());
    }

    public PluginHttpResponse saveRechargeSettings(PluginHttpRequest request) {
        WalletRechargeSettingsSaveRequest body = JsonSupport.read(request.body(), WalletRechargeSettingsSaveRequest.class);
        return PluginHttpResponse.ok(appService.saveRechargeSettings(assembler.toCmd(body)));
    }

    public PluginHttpResponse createRecharge(PluginHttpRequest request) {
        WalletRechargeCreateRequest body = JsonSupport.read(request.body(), WalletRechargeCreateRequest.class);
        Long principalUserId = request.principal().userId();
        boolean manage = request.principal().hasPermission(WalletPlugin.MANAGE_PERMISSION);
        String userId = body.userId();
        if (!manage) {
            if (principalUserId == null) {
                throw new IllegalArgumentException("请先登录");
            }
            String currentUserId = String.valueOf(principalUserId);
            if (body.userId() != null && !body.userId().isBlank() && !currentUserId.equals(body.userId().trim())) {
                throw new IllegalArgumentException("只能给自己的钱包充值");
            }
            userId = currentUserId;
        } else if (userId == null || userId.isBlank()) {
            userId = principalUserId == null ? null : String.valueOf(principalUserId);
        }
        return PluginHttpResponse.ok(appService.createRecharge(assembler.toCmd(new WalletRechargeCreateRequest(
                userId,
                body.assetCode(),
                body.channelCode(),
                body.payAmount(),
                body.productType(),
                body.remark()
        ))));
    }

    public PluginHttpResponse balances(PluginHttpRequest request) {
        String userId;
        if (request.principal().hasPermission(WalletPlugin.MANAGE_PERMISSION)) {
            userId = firstQuery(request, "userId");
            if (userId == null || userId.isBlank()) {
                Long principalUserId = request.principal().userId();
                userId = principalUserId == null ? null : String.valueOf(principalUserId);
            }
        } else {
            Long principalUserId = request.principal().userId();
            if (principalUserId == null) {
                throw new IllegalArgumentException("请先登录");
            }
            userId = String.valueOf(principalUserId);
        }
        return PluginHttpResponse.ok(appService.balances(userId).stream()
                .map(balance -> assembler.toRes(balance, userOf(balance.userId())))
                .toList());
    }

    public PluginHttpResponse adminBalances(PluginHttpRequest request) {
        String assetCode = firstQuery(request, "assetCode");
        return PluginHttpResponse.ok(appService.listBalances(assetCode, page(request), size(request)).stream()
                .map(balance -> assembler.toRes(balance, userOf(balance.userId())))
                .toList());
    }

    public PluginHttpResponse userBalances(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.balances(pathSegment(request.path(), 1)).stream()
                .map(balance -> assembler.toRes(balance, userOf(balance.userId())))
                .toList());
    }

    public PluginHttpResponse userBalance(PluginHttpRequest request) {
        var balance = appService.balance(pathSegment(request.path(), 1), pathSegment(request.path(), 3));
        return PluginHttpResponse.ok(assembler.toRes(balance, userOf(balance.userId())));
    }

    public PluginHttpResponse credit(PluginHttpRequest request) {
        WalletChangeRequest body = JsonSupport.read(request.body(), WalletChangeRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.credit(assembler.toCmd(body))));
    }

    public PluginHttpResponse debit(PluginHttpRequest request) {
        WalletChangeRequest body = JsonSupport.read(request.body(), WalletChangeRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.debit(assembler.toCmd(body))));
    }

    public PluginHttpResponse transfer(PluginHttpRequest request) {
        WalletTransferRequest body = JsonSupport.read(request.body(), WalletTransferRequest.class);
        Long principalUserId = request.principal().userId();
        boolean manage = request.principal().hasPermission(WalletPlugin.MANAGE_PERMISSION);
        String fromUserId = body.fromUserId();
        if (!manage) {
            if (principalUserId == null) {
                throw new IllegalArgumentException("请先登录");
            }
            String currentUserId = String.valueOf(principalUserId);
            if (body.fromUserId() != null && !body.fromUserId().isBlank() && !currentUserId.equals(body.fromUserId().trim())) {
                throw new IllegalArgumentException("只能从自己的钱包转出");
            }
            fromUserId = currentUserId;
        }
        String toUserId = resolveUserId(firstText(body.toAccount(), body.toUserId()), "转入用户不存在");
        WalletTransferCmd cmd = new WalletTransferCmd(fromUserId, toUserId, body.assetCode(),
                body.amount(), body.businessNo(), body.remark());
        return PluginHttpResponse.ok(assembler.toRes(appService.transfer(cmd), userOf(fromUserId), userOf(toUserId)));
    }

    public PluginHttpResponse transactions(PluginHttpRequest request) {
        String userId = resolveUserIdOrNull(firstQuery(request, "user"));
        if (userId == null) {
            userId = firstQuery(request, "userId");
        }
        return PluginHttpResponse.ok(appService.transactions(
                        firstQuery(request, "assetCode"),
                        firstQuery(request, "type"),
                        firstQuery(request, "source"),
                        userId,
                        longQuery(request, "startAt"),
                        longQuery(request, "endAt"),
                        page(request),
                        size(request)
                ).stream()
                .map(transaction -> assembler.toRes(transaction, userOf(transaction.fromUserId()), userOf(transaction.toUserId())))
                .toList());
    }

    public PluginHttpResponse myTransactions(PluginHttpRequest request) {
        Long principalUserId = request.principal().userId();
        if (principalUserId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return PluginHttpResponse.ok(appService.transactions(
                        firstQuery(request, "assetCode"),
                        firstQuery(request, "type"),
                        firstQuery(request, "source"),
                        String.valueOf(principalUserId),
                        longQuery(request, "startAt"),
                        longQuery(request, "endAt"),
                        page(request),
                        size(request)
                ).stream()
                .map(transaction -> assembler.toRes(transaction, userOf(transaction.fromUserId()), userOf(transaction.toUserId())))
                .toList());
    }

    public PluginHttpResponse transactionByBusinessNo(PluginHttpRequest request) {
        String businessNo = firstQuery(request, "businessNo");
        return appService.findTransactionByBusinessNo(businessNo)
                .map(assembler::toRes)
                .map(PluginHttpResponse::ok)
                .orElseGet(() -> PluginHttpResponse.rawJson(404, Map.of("message", "流水不存在")));
    }

    private int page(PluginHttpRequest request) {
        return intQuery(request, "page", 1);
    }

    private int size(PluginHttpRequest request) {
        return intQuery(request, "size", 20);
    }

    private int intQuery(PluginHttpRequest request, String key, int defaultValue) {
        List<String> values = request.query().get(key);
        return values == null || values.isEmpty() ? defaultValue : Integer.parseInt(values.get(0));
    }

    private Long longQuery(PluginHttpRequest request, String key) {
        List<String> values = request.query().get(key);
        return values == null || values.isEmpty() || values.get(0).isBlank() ? null : Long.parseLong(values.get(0));
    }

    private String firstQuery(PluginHttpRequest request, String key) {
        List<String> values = request.query().get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? null : second.trim();
    }

    private String resolveUserIdOrNull(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        return resolveUser(account).map(user -> String.valueOf(user.id())).orElse(account.trim());
    }

    private String resolveUserId(String account, String message) {
        return resolveUser(account)
                .map(user -> String.valueOf(user.id()))
                .orElseThrow(() -> new IllegalArgumentException(message + "：" + account));
    }

    private Optional<PluginUserProfile> resolveUser(String account) {
        if (account == null || account.isBlank()) {
            return Optional.empty();
        }
        String value = account.trim();
        if (value.contains("@")) {
            Optional<PluginUserProfile> byEmail = framework.users().findByEmail(value);
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }
        if (value.chars().allMatch(Character::isDigit)) {
            Optional<PluginUserProfile> byId = framework.users().findById(Long.parseLong(value));
            if (byId.isPresent()) {
                return byId;
            }
        }
        Optional<PluginUserProfile> byUsername = framework.users().findByUsername(value);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        return value.contains("@") ? Optional.empty() : framework.users().findByEmail(value);
    }

    private online.yudream.base.plugin.wallet.interfaces.res.WalletUserRes userOf(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return framework.users().findById(Long.parseLong(userId))
                    .map(user -> new online.yudream.base.plugin.wallet.interfaces.res.WalletUserRes(
                            String.valueOf(user.id()),
                            user.username(),
                            user.nickname(),
                            user.email(),
                            user.avatar()
                    ))
                    .orElse(null);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String pathSegment(String path, int index) {
        String[] segments = trim(path).split("/");
        return index >= 0 && index < segments.length ? decode(segments[index]) : null;
    }

    private String trim(String path) {
        String value = path == null ? "" : path.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private String decode(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
