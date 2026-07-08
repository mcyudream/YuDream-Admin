package online.yudream.base.plugin.wallet.application.service;

import online.yudream.base.plugin.spi.system.wallet.PluginWalletAsset;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletBalance;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletChangeRequest;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletService;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletTransaction;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletTransactionQuery;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletTransferRequest;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.payment.PluginPaymentChannel;
import online.yudream.base.plugin.spi.system.payment.PluginPaymentChannelInfo;
import online.yudream.base.plugin.spi.system.payment.PluginPaymentCreateRequest;
import online.yudream.base.plugin.spi.system.payment.PluginPaymentCreateResult;
import online.yudream.base.plugin.wallet.application.assembler.WalletAppAssembler;
import online.yudream.base.plugin.wallet.application.cmd.WalletAssetSaveCmd;
import online.yudream.base.plugin.wallet.application.cmd.WalletChangeCmd;
import online.yudream.base.plugin.wallet.application.cmd.WalletRechargeCreateCmd;
import online.yudream.base.plugin.wallet.application.cmd.WalletRechargeSettingsSaveCmd;
import online.yudream.base.plugin.wallet.application.cmd.WalletTransferCmd;
import online.yudream.base.plugin.wallet.application.dto.WalletPaymentChannelDTO;
import online.yudream.base.plugin.wallet.application.dto.WalletRechargeCreateDTO;
import online.yudream.base.plugin.wallet.application.dto.WalletRechargeOptionsDTO;
import online.yudream.base.plugin.wallet.application.dto.WalletRechargeRuleDTO;
import online.yudream.base.plugin.wallet.application.dto.WalletRechargeSettingsDTO;
import online.yudream.base.plugin.wallet.application.dto.WalletSummaryDTO;
import online.yudream.base.plugin.wallet.domain.aggregate.WalletAsset;
import online.yudream.base.plugin.wallet.domain.aggregate.WalletBalance;
import online.yudream.base.plugin.wallet.domain.aggregate.WalletTransaction;
import online.yudream.base.plugin.wallet.domain.enumerate.WalletTransactionType;
import online.yudream.base.plugin.wallet.domain.valobj.WalletDefaults;
import online.yudream.base.plugin.wallet.domain.valobj.WalletRechargeRule;
import online.yudream.base.plugin.wallet.domain.valobj.WalletRechargeSettings;
import online.yudream.base.plugin.wallet.infrastructure.repository.WalletRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WalletAppService implements PluginWalletService {

    private final WalletRepository repository;
    private final FrameworkServices framework;
    private final WalletAppAssembler assembler = new WalletAppAssembler();

    public WalletAppService(WalletRepository repository, FrameworkServices framework) {
        this.repository = repository;
        this.framework = framework;
    }

    public void initializeDefaults() {
        if (repository.defaultsInitialized()) {
            return;
        }
        if (repository.assetCount() > 0) {
            repository.markDefaultsInitialized();
            return;
        }
        for (WalletAsset asset : WalletDefaults.assets()) {
            repository.findAsset(asset.code()).orElseGet(() -> repository.saveAsset(asset));
        }
        repository.markDefaultsInitialized();
    }

    public WalletSummaryDTO summary() {
        initializeDefaults();
        return new WalletSummaryDTO(repository.assetCount(), repository.transactionCount());
    }

    public List<PluginWalletAsset> listAssets(int page, int size) {
        initializeDefaults();
        return repository.listAssets(page, size).stream().map(assembler::toSpi).toList();
    }

    @Override
    public List<PluginWalletAsset> assets() {
        return listAssets(1, 200);
    }

    @Override
    public Optional<PluginWalletAsset> findAsset(String assetCode) {
        initializeDefaults();
        return repository.findAsset(assetCode).map(assembler::toSpi);
    }

    @Override
    public PluginWalletAsset ensureAsset(PluginWalletAsset asset) {
        if (asset == null) {
            throw new IllegalArgumentException("资产不能为空");
        }
        WalletAsset existing = repository.findAsset(asset.code()).orElse(null);
        WalletAsset saved = existing == null
                ? repository.saveAsset(WalletAsset.create(asset.code(), asset.name(), asset.symbol(), asset.scale(), asset.money(), asset.minTransferAmount()))
                : repository.saveAsset(existing.update(asset.name(), asset.symbol(), asset.scale(), asset.enabled(), asset.transferEnabled(), asset.minTransferAmount()));
        return assembler.toSpi(saved);
    }

    public PluginWalletAsset saveAsset(WalletAssetSaveCmd cmd) {
        initializeDefaults();
        String code = WalletAsset.normalizeCode(cmd.code());
        WalletAsset existing = repository.findAsset(code).orElse(null);
        WalletAsset saved = existing == null
                ? repository.saveAsset(WalletAsset.create(
                code,
                cmd.name(),
                cmd.symbol(),
                cmd.scale() == null ? 0 : cmd.scale(),
                Boolean.TRUE.equals(cmd.money()),
                cmd.minTransferAmount()
        ))
                : repository.saveAsset(existing.update(cmd.name(), cmd.symbol(), cmd.scale(), cmd.enabled(), cmd.transferEnabled(), cmd.minTransferAmount()));
        return assembler.toSpi(saved);
    }

    public void deleteAsset(String assetCode) {
        initializeDefaults();
        String code = WalletAsset.normalizeCode(assetCode);
        requireAsset(code);
        if (!repository.findBalancesByAsset(code, 1, 1).isEmpty()) {
            throw new IllegalArgumentException("该币种已有余额记录，不能删除，可停用后保留历史账务");
        }
        if (!repository.findTransactionsByAsset(code, 1, 1).isEmpty()) {
            throw new IllegalArgumentException("该币种已有流水记录，不能删除，可停用后保留历史账务");
        }
        repository.deleteAsset(code);
    }

    public WalletRechargeSettingsDTO rechargeSettings() {
        initializeDefaults();
        return toDTO(settingsWithMoneyAssets());
    }

    public WalletRechargeSettingsDTO saveRechargeSettings(WalletRechargeSettingsSaveCmd cmd) {
        initializeDefaults();
        Map<String, WalletAsset> moneyAssets = moneyAssets().stream()
                .collect(Collectors.toMap(WalletAsset::code, Function.identity()));
        List<WalletRechargeRule> rules = cmd.rules() == null ? List.of() : cmd.rules().stream()
                .map(rule -> new WalletRechargeRule(
                        WalletAsset.normalizeCode(rule.assetCode()),
                        rule.enabled() == null || rule.enabled(),
                        rule.ratio(),
                        rule.minPayAmount(),
                        rule.maxPayAmount()
                ).normalized())
                .peek(rule -> {
                    WalletAsset asset = moneyAssets.get(rule.assetCode());
                    if (asset == null || !asset.money()) {
                        throw new IllegalArgumentException("只有货币类币种可以开启充值：" + rule.assetCode());
                    }
                })
                .toList();
        WalletRechargeSettings settings = new WalletRechargeSettings(
                cmd.enabled() == null || cmd.enabled(),
                cmd.defaultProductType(),
                rules
        ).normalized();
        return toDTO(repository.saveRechargeSettings(settings));
    }

    public WalletRechargeOptionsDTO rechargeOptions() {
        initializeDefaults();
        WalletRechargeSettings settings = settingsWithMoneyAssets();
        List<WalletPaymentChannelDTO> channels = paymentChannels().stream()
                .map(PluginPaymentChannel::info)
                .filter(PluginPaymentChannelInfo::enabled)
                .map(this::toDTO)
                .toList();
        Map<String, WalletRechargeRule> ruleMap = settings.rules().stream()
                .collect(Collectors.toMap(WalletRechargeRule::assetCode, Function.identity(), (first, second) -> first));
        List<PluginWalletAsset> rechargeableAssets = moneyAssets().stream()
                .filter(WalletAsset::enabled)
                .filter(asset -> {
                    WalletRechargeRule rule = ruleMap.get(asset.code());
                    return rule != null && rule.enabled();
                })
                .map(assembler::toSpi)
                .toList();
        boolean enabled = settings.enabled() && !channels.isEmpty() && !rechargeableAssets.isEmpty();
        return new WalletRechargeOptionsDTO(enabled, settings.defaultProductType(), channels,
                rechargeableAssets, settings.rules().stream().map(this::toDTO).toList());
    }

    public WalletRechargeCreateDTO createRecharge(WalletRechargeCreateCmd cmd) {
        initializeDefaults();
        WalletRechargeSettings settings = settingsWithMoneyAssets();
        if (!settings.enabled()) {
            throw new IllegalArgumentException("钱包充值未启用");
        }
        WalletAsset asset = requireAsset(cmd.assetCode()).requireEnabled();
        if (!asset.money()) {
            throw new IllegalArgumentException("只有货币类币种可以充值：" + asset.code());
        }
        WalletRechargeRule rule = settings.rules().stream()
                .filter(item -> item.assetCode().equals(asset.code()))
                .findFirst()
                .orElseGet(() -> WalletRechargeRule.defaults(asset.code()));
        BigDecimal payAmount = requirePayAmount(cmd.payAmount());
        rule.validatePayAmount(payAmount);
        BigDecimal walletAmount = payAmount.multiply(rule.ratio()).setScale(asset.scale(), RoundingMode.DOWN);
        if (walletAmount.signum() <= 0) {
            throw new IllegalArgumentException("到账金额必须大于 0");
        }
        PluginPaymentChannel channel = paymentChannels().stream()
                .filter(item -> item.info().enabled())
                .filter(item -> item.info().code().equalsIgnoreCase(requireText(cmd.channelCode(), "支付渠道不能为空")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("支付渠道不可用：" + cmd.channelCode()));
        String productType = resolveProductType(cmd.productType(), settings.defaultProductType(), channel.info().productTypes());
        PluginPaymentCreateResult result = channel.createRecharge(new PluginPaymentCreateRequest(
                requireText(cmd.userId(), "用户不能为空"),
                asset.code(),
                payAmount,
                walletAmount,
                asset.name() + "充值",
                trimToNull(cmd.remark()),
                productType
        ));
        return new WalletRechargeCreateDTO(
                result.channelCode(),
                result.channelName(),
                result.outTradeNo(),
                result.assetCode(),
                result.payAmount(),
                result.walletAmount(),
                result.productType(),
                result.status(),
                result.payloadType(),
                result.payPayload(),
                result.createdAt()
        );
    }

    @Override
    public List<PluginWalletBalance> balances(String userId) {
        initializeDefaults();
        String owner = requireText(userId, "用户不能为空");
        return repository.findBalancesByUser(owner).stream().map(assembler::toSpi).toList();
    }

    @Override
    public PluginWalletBalance balance(String userId, String assetCode) {
        initializeDefaults();
        WalletAsset asset = requireAsset(assetCode);
        return assembler.toSpi(balanceOf(requireText(userId, "用户不能为空"), asset));
    }

    @Override
    public List<PluginWalletBalance> listBalances(String assetCode, int page, int size) {
        initializeDefaults();
        List<WalletBalance> items = hasText(assetCode)
                ? repository.findBalancesByAsset(assetCode, page, size)
                : repository.listBalances(page, size);
        return items.stream().map(assembler::toSpi).toList();
    }

    public Map<String, Map<String, BigDecimal>> historicalIncomeTotals() {
        initializeDefaults();
        return repository.listTransactions(1, transactionFetchSize()).stream()
                .filter(transaction -> transaction.type() == WalletTransactionType.CREDIT)
                .filter(transaction -> hasText(transaction.toUserId()))
                .filter(transaction -> hasText(transaction.assetCode()))
                .filter(this::isHistoricalIncomeSource)
                .collect(Collectors.groupingBy(WalletTransaction::toUserId,
                        Collectors.groupingBy(WalletTransaction::assetCode,
                                Collectors.mapping(WalletTransaction::amount,
                                        Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)))));
    }

    @Override
    public List<PluginWalletTransaction> transactions(PluginWalletTransactionQuery query) {
        PluginWalletTransactionQuery safeQuery = query == null
                ? new PluginWalletTransactionQuery(null, null, null, null, null, null, 1, 20)
                : query;
        return transactions(
                safeQuery.assetCode(),
                safeQuery.type(),
                safeQuery.source(),
                safeQuery.userId(),
                safeQuery.startAt(),
                safeQuery.endAt(),
                safeQuery.page(),
                safeQuery.size()
        );
    }

    public List<PluginWalletTransaction> transactions(int page, int size) {
        return repository.listTransactions(page, size).stream().map(assembler::toSpi).toList();
    }

    public List<PluginWalletTransaction> transactions(String assetCode, String type, String source, String userId, int page, int size) {
        return transactions(assetCode, type, source, userId, null, null, page, size);
    }

    public List<PluginWalletTransaction> transactions(String assetCode, String type, String source, String userId, Long startAt, Long endAt, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        return repository.listTransactions(1, transactionFetchSize()).stream()
                .filter(item -> !hasText(assetCode) || item.assetCode().equalsIgnoreCase(assetCode.trim()))
                .filter(item -> !hasText(type) || item.type().name().equalsIgnoreCase(type.trim()))
                .filter(item -> !hasText(source) || item.source().equalsIgnoreCase(source.trim()))
                .filter(item -> !hasText(userId) || userId.trim().equals(item.fromUserId()) || userId.trim().equals(item.toUserId()))
                .filter(item -> startAt == null || item.createdAt() >= startAt)
                .filter(item -> endAt == null || item.createdAt() < endAt)
                .skip((long) (safePage - 1) * safeSize)
                .limit(safeSize)
                .map(assembler::toSpi)
                .toList();
    }

    @Override
    public PluginWalletTransaction credit(PluginWalletChangeRequest request) {
        return credit(new WalletChangeCmd(
                request.userId(),
                request.assetCode(),
                request.amount(),
                request.businessNo(),
                request.remark()
        ));
    }

    public PluginWalletTransaction credit(WalletChangeCmd cmd) {
        initializeDefaults();
        String businessNo = normalizeBusinessNo(cmd.businessNo());
        Optional<WalletTransaction> existing = findExisting(businessNo);
        if (existing.isPresent()) {
            return assembler.toSpi(existing.get());
        }
        WalletAsset asset = requireAsset(cmd.assetCode()).requireEnabled();
        BigDecimal amount = asset.normalize(cmd.amount());
        String userId = requireText(cmd.userId(), "用户不能为空");
        Optional<WalletBalance> previousBalance = repository.findBalance(userId, asset.code());
        WalletBalance balance = previousBalance.orElseGet(() -> WalletBalance.empty(userId, asset.code(), asset.scale())).credit(amount);
        WalletTransaction transaction;
        try {
            WalletBalance savedBalance = repository.saveBalance(balance);
            transaction = repository.saveTransaction(WalletTransaction.credit(
                    businessNo,
                    asset.code(),
                    savedBalance.userId(),
                    amount,
                    savedBalance.balance(),
                    trimToNull(cmd.remark())
            ));
        } catch (RuntimeException ex) {
            rollbackBalance(previousBalance, userId, asset.code(), ex);
            throw ex;
        }
        return assembler.toSpi(transaction);
    }

    @Override
    public PluginWalletTransaction debit(PluginWalletChangeRequest request) {
        return debit(new WalletChangeCmd(
                request.userId(),
                request.assetCode(),
                request.amount(),
                request.businessNo(),
                request.remark()
        ));
    }

    public PluginWalletTransaction debit(WalletChangeCmd cmd) {
        initializeDefaults();
        String businessNo = normalizeBusinessNo(cmd.businessNo());
        Optional<WalletTransaction> existing = findExisting(businessNo);
        if (existing.isPresent()) {
            return assembler.toSpi(existing.get());
        }
        WalletAsset asset = requireAsset(cmd.assetCode()).requireEnabled();
        BigDecimal amount = asset.normalize(cmd.amount());
        String userId = requireText(cmd.userId(), "用户不能为空");
        Optional<WalletBalance> previousBalance = repository.findBalance(userId, asset.code());
        WalletBalance balance = previousBalance.orElseGet(() -> WalletBalance.empty(userId, asset.code(), asset.scale())).debit(amount);
        WalletTransaction transaction;
        try {
            WalletBalance savedBalance = repository.saveBalance(balance);
            transaction = repository.saveTransaction(WalletTransaction.debit(
                    businessNo,
                    asset.code(),
                    savedBalance.userId(),
                    amount,
                    savedBalance.balance(),
                    trimToNull(cmd.remark())
            ));
        } catch (RuntimeException ex) {
            rollbackBalance(previousBalance, userId, asset.code(), ex);
            throw ex;
        }
        return assembler.toSpi(transaction);
    }

    @Override
    public PluginWalletTransaction transfer(PluginWalletTransferRequest request) {
        return transfer(new WalletTransferCmd(
                request.fromUserId(),
                request.toUserId(),
                request.assetCode(),
                request.amount(),
                request.businessNo(),
                request.remark()
        ));
    }

    public PluginWalletTransaction transfer(WalletTransferCmd cmd) {
        initializeDefaults();
        String businessNo = normalizeBusinessNo(cmd.businessNo());
        Optional<WalletTransaction> existing = findExisting(businessNo);
        if (existing.isPresent()) {
            return assembler.toSpi(existing.get());
        }
        String fromUserId = requireText(cmd.fromUserId(), "转出用户不能为空");
        String toUserId = requireText(cmd.toUserId(), "转入用户不能为空");
        if (fromUserId.equals(toUserId)) {
            throw new IllegalArgumentException("不能给自己转账");
        }
        WalletAsset asset = requireAsset(cmd.assetCode()).requireTransferEnabled();
        asset.validateTransferAmount(cmd.amount());
        BigDecimal amount = asset.normalize(cmd.amount());
        Optional<WalletBalance> previousFrom = repository.findBalance(fromUserId, asset.code());
        Optional<WalletBalance> previousTo = repository.findBalance(toUserId, asset.code());
        WalletBalance from = previousFrom.orElseGet(() -> WalletBalance.empty(fromUserId, asset.code(), asset.scale())).debit(amount);
        WalletBalance to = previousTo.orElseGet(() -> WalletBalance.empty(toUserId, asset.code(), asset.scale())).credit(amount);
        WalletTransaction transaction;
        try {
            WalletBalance savedFrom = repository.saveBalance(from);
            WalletBalance savedTo = repository.saveBalance(to);
            transaction = repository.saveTransaction(WalletTransaction.transfer(
                    businessNo,
                    asset.code(),
                    fromUserId,
                    toUserId,
                    amount,
                    savedFrom.balance(),
                    savedTo.balance(),
                    trimToNull(cmd.remark())
            ));
        } catch (RuntimeException ex) {
            rollbackBalance(previousTo, toUserId, asset.code(), ex);
            rollbackBalance(previousFrom, fromUserId, asset.code(), ex);
            throw ex;
        }
        return assembler.toSpi(transaction);
    }

    @Override
    public Optional<PluginWalletTransaction> findTransactionByBusinessNo(String businessNo) {
        return repository.findTransactionByBusinessNo(businessNo).map(assembler::toSpi);
    }

    private Optional<WalletTransaction> findExisting(String businessNo) {
        return businessNo == null ? Optional.empty() : repository.findTransactionByBusinessNo(businessNo);
    }

    private WalletAsset requireAsset(String assetCode) {
        return repository.findAsset(WalletAsset.normalizeCode(assetCode))
                .orElseThrow(() -> new IllegalArgumentException("资产不存在：" + assetCode));
    }

    private WalletBalance balanceOf(String userId, WalletAsset asset) {
        return repository.findBalance(userId, asset.code())
                .orElseGet(() -> WalletBalance.empty(userId, asset.code(), asset.scale()));
    }

    private void rollbackBalance(Optional<WalletBalance> previous, String userId, String assetCode, RuntimeException original) {
        try {
            if (previous.isPresent()) {
                repository.saveBalance(previous.get());
            } else {
                repository.deleteBalance(userId, assetCode);
            }
        } catch (RuntimeException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    private WalletRechargeSettings settingsWithMoneyAssets() {
        List<String> moneyAssetCodes = moneyAssets().stream().map(WalletAsset::code).toList();
        WalletRechargeSettings existing = repository.rechargeSettings()
                .orElseGet(() -> WalletRechargeSettings.defaults(moneyAssetCodes));
        Map<String, WalletRechargeRule> existingRules = existing.rules().stream()
                .collect(Collectors.toMap(WalletRechargeRule::assetCode, Function.identity(), (first, second) -> first));
        List<WalletRechargeRule> rules = moneyAssetCodes.stream()
                .map(code -> existingRules.getOrDefault(code, WalletRechargeRule.defaults(code)))
                .toList();
        return new WalletRechargeSettings(existing.enabled(), existing.defaultProductType(), rules).normalized();
    }

    private List<WalletAsset> moneyAssets() {
        return repository.listAssets(1, 500).stream()
                .filter(WalletAsset::money)
                .toList();
    }

    private List<PluginPaymentChannel> paymentChannels() {
        return framework == null ? List.of() : framework.extensions(PluginPaymentChannel.class);
    }

    private WalletRechargeSettingsDTO toDTO(WalletRechargeSettings settings) {
        return new WalletRechargeSettingsDTO(settings.enabled(), settings.defaultProductType(),
                settings.rules().stream().map(this::toDTO).toList());
    }

    private WalletRechargeRuleDTO toDTO(WalletRechargeRule rule) {
        return new WalletRechargeRuleDTO(rule.assetCode(), rule.enabled(), rule.ratio(), rule.minPayAmount(), rule.maxPayAmount());
    }

    private WalletPaymentChannelDTO toDTO(PluginPaymentChannelInfo channel) {
        return new WalletPaymentChannelDTO(channel.code(), channel.name(), channel.icon(), channel.description(),
                channel.enabled(), channel.productTypes());
    }

    private String resolveProductType(String requested, String defaultProductType, List<String> supportedProductTypes) {
        String productType = hasText(requested)
                ? requested.trim().toUpperCase()
                : (hasText(defaultProductType) ? defaultProductType.trim().toUpperCase() : null);
        List<String> supported = supportedProductTypes == null ? List.of() : supportedProductTypes.stream()
                .filter(this::hasText)
                .map(item -> item.trim().toUpperCase())
                .toList();
        if (supported.isEmpty()) {
            return productType;
        }
        if (!hasText(productType)) {
            return supported.get(0);
        }
        if (supported.contains(productType)) {
            return productType;
        }
        if (!hasText(requested)) {
            return supported.contains("PAGE") ? "PAGE" : supported.get(0);
        }
        throw new IllegalArgumentException("支付渠道不支持该支付产品：" + productType);
    }

    private BigDecimal requirePayAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("支付金额必须大于 0");
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() > 2) {
            throw new IllegalArgumentException("支付金额最多支持 2 位小数");
        }
        return amount.setScale(2);
    }

    private String normalizeBusinessNo(String businessNo) {
        String value = trimToNull(businessNo);
        if (value != null && value.length() > 96) {
            throw new IllegalArgumentException("业务单号不能超过 96 个字符");
        }
        return value;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isHistoricalIncomeSource(WalletTransaction transaction) {
        return !"MINECRAFT_SEASON".equals(transaction.source());
    }

    private int transactionFetchSize() {
        long count = repository.transactionCount();
        if (count <= 0) {
            return 1;
        }
        return (int) Math.min(count, Integer.MAX_VALUE);
    }
}
