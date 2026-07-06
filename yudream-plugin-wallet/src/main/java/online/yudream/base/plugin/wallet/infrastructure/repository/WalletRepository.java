package online.yudream.base.plugin.wallet.infrastructure.repository;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.wallet.domain.aggregate.WalletAsset;
import online.yudream.base.plugin.wallet.domain.aggregate.WalletBalance;
import online.yudream.base.plugin.wallet.domain.aggregate.WalletTransaction;
import online.yudream.base.plugin.wallet.domain.enumerate.WalletTransactionType;
import online.yudream.base.plugin.wallet.domain.valobj.WalletRechargeRule;
import online.yudream.base.plugin.wallet.domain.valobj.WalletRechargeSettings;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WalletRepository {

    private static final String ASSETS = "assets";
    private static final String BALANCES = "balances";
    private static final String TRANSACTIONS = "transactions";
    private static final String META = "meta";
    private static final String OPTIONS = "options";
    private static final String DEFAULTS_META_ID = "defaults";
    private static final String RECHARGE_SETTINGS_ID = "recharge-settings";

    private final PluginDocumentStore documents;

    public WalletRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    public WalletAsset saveAsset(WalletAsset asset) {
        return toAsset(documents.save(ASSETS, asset.code(), assetDocument(asset)));
    }

    public Optional<WalletAsset> findAsset(String code) {
        return documents.findById(ASSETS, normalizeAssetCode(code)).map(this::toAsset);
    }

    public void deleteAsset(String code) {
        documents.delete(ASSETS, normalizeAssetCode(code));
    }

    public List<WalletAsset> listAssets(int page, int size) {
        return documents.findAll(ASSETS, page, size).stream().map(this::toAsset).toList();
    }

    public long assetCount() {
        return documents.count(ASSETS);
    }

    public WalletBalance saveBalance(WalletBalance balance) {
        return toBalance(documents.save(BALANCES, balanceId(balance.userId(), balance.assetCode()), balanceDocument(balance)));
    }

    public void deleteBalance(String userId, String assetCode) {
        documents.delete(BALANCES, balanceId(userId, assetCode));
    }

    public Optional<WalletBalance> findBalance(String userId, String assetCode) {
        return documents.findById(BALANCES, balanceId(userId, assetCode)).map(this::toBalance);
    }

    public List<WalletBalance> findBalancesByUser(String userId) {
        return documents.findByField(BALANCES, "userId", userId, 1, 200).stream().map(this::toBalance).toList();
    }

    public List<WalletBalance> listBalances(int page, int size) {
        return documents.findAll(BALANCES, page, size).stream().map(this::toBalance).toList();
    }

    public List<WalletBalance> findBalancesByAsset(String assetCode, int page, int size) {
        return documents.findByField(BALANCES, "assetCode", normalizeAssetCode(assetCode), page, size).stream().map(this::toBalance).toList();
    }

    public WalletTransaction saveTransaction(WalletTransaction transaction) {
        return toTransaction(documents.save(TRANSACTIONS, transaction.id(), transactionDocument(transaction)));
    }

    public Optional<WalletTransaction> findTransaction(String id) {
        return documents.findById(TRANSACTIONS, id).map(this::toTransaction);
    }

    public Optional<WalletTransaction> findTransactionByBusinessNo(String businessNo) {
        if (businessNo == null || businessNo.isBlank()) {
            return Optional.empty();
        }
        return documents.findByField(TRANSACTIONS, "businessNo", businessNo.trim(), 1, 1).stream()
                .findFirst()
                .map(this::toTransaction);
    }

    public List<WalletTransaction> listTransactions(int page, int size) {
        return documents.findAll(TRANSACTIONS, page, size).stream().map(this::toTransaction).toList();
    }

    public List<WalletTransaction> findTransactionsByAsset(String assetCode, int page, int size) {
        return documents.findByField(TRANSACTIONS, "assetCode", normalizeAssetCode(assetCode), page, size).stream().map(this::toTransaction).toList();
    }

    public long transactionCount() {
        return documents.count(TRANSACTIONS);
    }

    public Optional<WalletRechargeSettings> rechargeSettings() {
        return documents.findById(OPTIONS, RECHARGE_SETTINGS_ID).map(this::toRechargeSettings);
    }

    public WalletRechargeSettings saveRechargeSettings(WalletRechargeSettings settings) {
        return toRechargeSettings(documents.save(OPTIONS, RECHARGE_SETTINGS_ID, rechargeSettingsDocument(settings.normalized())));
    }

    public boolean defaultsInitialized() {
        return documents.findById(META, DEFAULTS_META_ID).isPresent();
    }

    public void markDefaultsInitialized() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("initialized", true);
        document.put("updatedAt", System.currentTimeMillis());
        documents.save(META, DEFAULTS_META_ID, document);
    }

    private Map<String, Object> assetDocument(WalletAsset asset) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("code", asset.code());
        document.put("name", asset.name());
        document.put("symbol", asset.symbol());
        document.put("scale", asset.scale());
        document.put("money", asset.money());
        document.put("enabled", asset.enabled());
        document.put("transferEnabled", asset.transferEnabled());
        document.put("minTransferAmount", string(asset.minTransferAmount()));
        document.put("createdAt", asset.createdAt());
        document.put("updatedAt", asset.updatedAt());
        return document;
    }

    private Map<String, Object> balanceDocument(WalletBalance balance) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("userId", balance.userId());
        document.put("assetCode", balance.assetCode());
        document.put("balance", string(balance.balance()));
        document.put("updatedAt", balance.updatedAt());
        return document;
    }

    private Map<String, Object> transactionDocument(WalletTransaction transaction) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("businessNo", transaction.businessNo());
        document.put("type", transaction.type().name());
        document.put("source", transaction.source());
        document.put("assetCode", transaction.assetCode());
        document.put("fromUserId", transaction.fromUserId());
        document.put("toUserId", transaction.toUserId());
        document.put("amount", string(transaction.amount()));
        document.put("fromBalanceAfter", string(transaction.fromBalanceAfter()));
        document.put("toBalanceAfter", string(transaction.toBalanceAfter()));
        document.put("remark", transaction.remark());
        document.put("createdAt", transaction.createdAt());
        return document;
    }

    private Map<String, Object> rechargeSettingsDocument(WalletRechargeSettings settings) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("enabled", settings.enabled());
        document.put("defaultProductType", settings.defaultProductType());
        document.put("rules", settings.rules().stream().map(this::rechargeRuleDocument).toList());
        return document;
    }

    private Map<String, Object> rechargeRuleDocument(WalletRechargeRule rule) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("assetCode", rule.assetCode());
        document.put("enabled", rule.enabled());
        document.put("ratio", string(rule.ratio()));
        document.put("minPayAmount", string(rule.minPayAmount()));
        document.put("maxPayAmount", string(rule.maxPayAmount()));
        return document;
    }

    private WalletAsset toAsset(Map<String, Object> document) {
        return new WalletAsset(
                string(document, "code", "id"),
                string(document, "name"),
                string(document, "symbol"),
                integer(document, "scale", 0),
                bool(document, "money", false),
                bool(document, "enabled", true),
                bool(document, "transferEnabled", true),
                decimal(document, "minTransferAmount", BigDecimal.ZERO),
                number(document, "createdAt", 0L),
                number(document, "updatedAt", 0L)
        );
    }

    private WalletBalance toBalance(Map<String, Object> document) {
        return new WalletBalance(
                string(document, "userId"),
                string(document, "assetCode"),
                decimal(document, "balance", BigDecimal.ZERO),
                number(document, "updatedAt", 0L)
        );
    }

    private WalletTransaction toTransaction(Map<String, Object> document) {
        return new WalletTransaction(
                string(document, "id"),
                string(document, "businessNo"),
                WalletTransactionType.valueOf(string(document, "type")),
                string(document, "source") == null ? WalletTransaction.sourceOf(string(document, "businessNo"), fallbackSource(string(document, "type"))) : string(document, "source"),
                string(document, "assetCode"),
                string(document, "fromUserId"),
                string(document, "toUserId"),
                decimal(document, "amount", BigDecimal.ZERO),
                decimal(document, "fromBalanceAfter", null),
                decimal(document, "toBalanceAfter", null),
                string(document, "remark"),
                number(document, "createdAt", 0L)
        );
    }

    @SuppressWarnings("unchecked")
    private WalletRechargeSettings toRechargeSettings(Map<String, Object> document) {
        Object rulesValue = document.get("rules");
        List<WalletRechargeRule> rules = rulesValue instanceof List<?> items
                ? items.stream()
                .filter(Map.class::isInstance)
                .map(item -> toRechargeRule((Map<String, Object>) item))
                .toList()
                : List.of();
        return new WalletRechargeSettings(
                bool(document, "enabled", true),
                string(document, "defaultProductType") == null ? "PAGE" : string(document, "defaultProductType"),
                rules
        ).normalized();
    }

    private WalletRechargeRule toRechargeRule(Map<String, Object> document) {
        return new WalletRechargeRule(
                string(document, "assetCode"),
                bool(document, "enabled", true),
                decimal(document, "ratio", BigDecimal.ONE),
                decimal(document, "minPayAmount", BigDecimal.ZERO),
                decimal(document, "maxPayAmount", null)
        ).normalized();
    }

    private String fallbackSource(String type) {
        if ("TRANSFER".equals(type)) {
            return "TRANSFER";
        }
        return "ADMIN";
    }

    private String balanceId(String userId, String assetCode) {
        return requireText(userId, "用户不能为空") + ":" + normalizeAssetCode(assetCode);
    }

    private String normalizeAssetCode(String code) {
        return WalletAsset.normalizeCode(code);
    }

    private String string(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String string(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String string(Map<String, Object> document, String key, String fallbackKey) {
        String value = string(document, key);
        return value == null ? string(document, fallbackKey) : value;
    }

    private Integer integer(Map<String, Object> document, String key, Integer defaultValue) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null || String.valueOf(value).isBlank() ? defaultValue : Integer.parseInt(String.valueOf(value));
    }

    private Long number(Map<String, Object> document, String key, Long defaultValue) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null || String.valueOf(value).isBlank() ? defaultValue : Long.parseLong(String.valueOf(value));
    }

    private Boolean bool(Map<String, Object> document, String key, Boolean defaultValue) {
        Object value = document.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null || String.valueOf(value).isBlank() ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private BigDecimal decimal(Map<String, Object> document, String key, BigDecimal defaultValue) {
        Object value = document.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
