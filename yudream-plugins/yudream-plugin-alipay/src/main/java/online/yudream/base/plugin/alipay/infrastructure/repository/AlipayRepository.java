package online.yudream.base.plugin.alipay.infrastructure.repository;

import online.yudream.base.plugin.alipay.domain.aggregate.AlipayRechargeOrder;
import online.yudream.base.plugin.alipay.domain.enumerate.AlipayOrderStatus;
import online.yudream.base.plugin.alipay.domain.enumerate.AlipayProductType;
import online.yudream.base.plugin.alipay.domain.valobj.AlipayConfig;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AlipayRepository {

    private static final String OPTIONS = "options";
    private static final String ORDERS = "orders";
    private static final String CONFIG_ID = "config";

    private final PluginDocumentStore documents;

    public AlipayRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    public AlipayConfig config() {
        return documents.findById(OPTIONS, CONFIG_ID)
                .map(this::toConfig)
                .orElseGet(AlipayConfig::defaults);
    }

    public AlipayConfig saveConfig(AlipayConfig config) {
        return toConfig(documents.save(OPTIONS, CONFIG_ID, configDocument(config.normalized())));
    }

    public AlipayRechargeOrder saveOrder(AlipayRechargeOrder order) {
        return toOrder(documents.save(ORDERS, order.outTradeNo(), orderDocument(order)));
    }

    public Optional<AlipayRechargeOrder> findOrder(String outTradeNo) {
        return documents.findById(ORDERS, outTradeNo).map(this::toOrder);
    }

    public List<AlipayRechargeOrder> listOrders(int page, int size) {
        return documents.findAll(ORDERS, page, size).stream().map(this::toOrder).toList();
    }

    private Map<String, Object> configDocument(AlipayConfig config) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("appId", config.appId());
        document.put("privateKey", config.privateKey());
        document.put("alipayPublicKey", config.alipayPublicKey());
        document.put("gatewayUrl", config.gatewayUrl());
        document.put("notifyUrl", config.notifyUrl());
        document.put("returnUrl", config.returnUrl());
        document.put("signType", config.signType());
        document.put("charset", config.charset());
        document.put("enabled", config.enabled());
        return document;
    }

    private Map<String, Object> orderDocument(AlipayRechargeOrder order) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("outTradeNo", order.outTradeNo());
        document.put("userId", order.userId());
        document.put("assetCode", order.assetCode());
        document.put("amount", order.amount().toPlainString());
        document.put("walletAmount", order.walletAmount().toPlainString());
        document.put("subject", order.subject());
        document.put("body", order.body());
        document.put("productType", order.productType().name());
        document.put("status", order.status().name());
        document.put("tradeNo", order.tradeNo());
        document.put("orderString", order.orderString());
        document.put("walletTransactionId", order.walletTransactionId());
        document.put("createdAt", order.createdAt());
        document.put("updatedAt", order.updatedAt());
        document.put("paidAt", order.paidAt());
        return document;
    }

    private AlipayConfig toConfig(Map<String, Object> document) {
        return new AlipayConfig(
                string(document, "appId"),
                string(document, "privateKey"),
                string(document, "alipayPublicKey"),
                string(document, "gatewayUrl"),
                string(document, "notifyUrl"),
                string(document, "returnUrl"),
                string(document, "signType"),
                string(document, "charset"),
                bool(document, "enabled", false)
        ).normalized();
    }

    private AlipayRechargeOrder toOrder(Map<String, Object> document) {
        return new AlipayRechargeOrder(
                string(document, "outTradeNo", "id"),
                string(document, "userId"),
                string(document, "assetCode"),
                decimal(document, "amount", BigDecimal.ZERO),
                decimal(document, "walletAmount", decimal(document, "amount", BigDecimal.ZERO)),
                string(document, "subject"),
                string(document, "body"),
                AlipayProductType.valueOf(string(document, "productType")),
                AlipayOrderStatus.valueOf(string(document, "status")),
                string(document, "tradeNo"),
                string(document, "orderString"),
                string(document, "walletTransactionId"),
                number(document, "createdAt", 0L),
                number(document, "updatedAt", 0L),
                number(document, "paidAt", 0L)
        );
    }

    private String string(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String string(Map<String, Object> document, String key, String fallbackKey) {
        String value = string(document, key);
        return value == null ? string(document, fallbackKey) : value;
    }

    private Boolean bool(Map<String, Object> document, String key, Boolean defaultValue) {
        Object value = document.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null || String.valueOf(value).isBlank() ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private Long number(Map<String, Object> document, String key, Long defaultValue) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null || String.valueOf(value).isBlank() ? defaultValue : Long.parseLong(String.valueOf(value));
    }

    private BigDecimal decimal(Map<String, Object> document, String key, BigDecimal defaultValue) {
        Object value = document.get(key);
        return value == null || String.valueOf(value).isBlank() ? defaultValue : new BigDecimal(String.valueOf(value));
    }
}
