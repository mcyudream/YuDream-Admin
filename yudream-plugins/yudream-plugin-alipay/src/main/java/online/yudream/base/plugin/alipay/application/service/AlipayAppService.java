package online.yudream.base.plugin.alipay.application.service;

import online.yudream.base.plugin.alipay.application.assembler.AlipayAppAssembler;
import online.yudream.base.plugin.alipay.application.cmd.AlipayConfigSaveCmd;
import online.yudream.base.plugin.alipay.application.cmd.AlipayRechargeCreateCmd;
import online.yudream.base.plugin.alipay.application.dto.AlipayNotifyResultDTO;
import online.yudream.base.plugin.alipay.application.dto.AlipayRechargeCreateDTO;
import online.yudream.base.plugin.alipay.domain.aggregate.AlipayRechargeOrder;
import online.yudream.base.plugin.alipay.domain.enumerate.AlipayOrderStatus;
import online.yudream.base.plugin.alipay.domain.enumerate.AlipayProductType;
import online.yudream.base.plugin.alipay.domain.valobj.AlipayConfig;
import online.yudream.base.plugin.alipay.infrastructure.repository.AlipayRepository;
import online.yudream.base.plugin.alipay.infrastructure.service.AlipayGatewayService;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletAsset;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletChangeRequest;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletService;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletTransaction;
import online.yudream.base.plugin.spi.system.payment.PluginPaymentChannelInfo;
import online.yudream.base.plugin.spi.system.payment.PluginPaymentCreateRequest;
import online.yudream.base.plugin.spi.system.payment.PluginPaymentCreateResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class AlipayAppService {

    public static final String CHANNEL_CODE = "alipay";
    public static final String CHANNEL_NAME = "支付宝";
    private static final AlipayProductType SUPPORTED_PRODUCT_TYPE = AlipayProductType.PAGE;
    private static final String TRADE_SUCCESS = "TRADE_SUCCESS";
    private static final String TRADE_FINISHED = "TRADE_FINISHED";
    private static final String APP_WEB_URL_SETTING = "app.web-url";
    private static final String APP_BASE_URL_SETTING = "app.base-url";
    private static final String NOTIFY_PATH = "/api/plugins/yudream-alipay/notify";
    private static final String RETURN_PATH = "/pay/result";

    private final AlipayRepository repository;
    private final AlipayGatewayService gatewayService;
    private final PluginWalletService walletService;
    private final FrameworkServices frameworkServices;
    private final AlipayAppAssembler assembler = new AlipayAppAssembler();

    public AlipayAppService(AlipayRepository repository, AlipayGatewayService gatewayService,
                            PluginWalletService walletService, FrameworkServices frameworkServices) {
        this.repository = repository;
        this.gatewayService = gatewayService;
        this.walletService = walletService;
        this.frameworkServices = frameworkServices;
    }

    public AlipayConfig config() {
        return effectiveConfig(repository.config()).withoutSecrets();
    }

    public AlipayConfig saveConfig(AlipayConfigSaveCmd cmd) {
        AlipayConfig config = storageConfig(assembler.merge(repository.config(), cmd));
        return effectiveConfig(repository.saveConfig(config)).withoutSecrets();
    }

    public PluginPaymentChannelInfo channelInfo() {
        return new PluginPaymentChannelInfo(
                CHANNEL_CODE,
                CHANNEL_NAME,
                "i-ri:alipay-line",
                "支付宝官方收款 API",
                channelEnabled(),
                List.of(SUPPORTED_PRODUCT_TYPE.name())
        );
    }

    public boolean channelEnabled() {
        try {
            effectiveConfig(repository.config()).ensureUsable();
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public PluginPaymentCreateResult createRecharge(PluginPaymentCreateRequest request) {
        AlipayRechargeCreateDTO dto = createRecharge(new AlipayRechargeCreateCmd(
                request.userId(),
                request.assetCode(),
                request.payAmount(),
                request.walletAmount(),
                request.subject(),
                request.body(),
                request.productType()
        ), null);
        AlipayRechargeOrder order = dto.order();
        return new PluginPaymentCreateResult(
                CHANNEL_CODE,
                CHANNEL_NAME,
                order.outTradeNo(),
                order.assetCode(),
                order.amount(),
                order.walletAmount(),
                order.productType().name(),
                order.status().name(),
                payloadType(order.productType()),
                dto.payPayload(),
                order.createdAt()
        );
    }

    public AlipayRechargeCreateDTO createRecharge(AlipayRechargeCreateCmd cmd, Long currentUserId) {
        AlipayConfig config = effectiveConfig(repository.config());
        config.ensureUsable();
        String userId = ownerId(cmd.userId(), currentUserId);
        String assetCode = assetCode(cmd.assetCode());
        ensureMoneyAsset(assetCode);
        AlipayRechargeOrder order = AlipayRechargeOrder.create(
                userId,
                assetCode,
                cmd.amount(),
                cmd.walletAmount(),
                cmd.subject(),
                cmd.body(),
                productType(cmd.productType())
        );
        String payload = gatewayService.createPayPayload(config, order);
        AlipayRechargeOrder saved = repository.saveOrder(order.withOrderString(payload));
        return new AlipayRechargeCreateDTO(saved, payload);
    }

    public AlipayNotifyResultDTO handleNotify(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return new AlipayNotifyResultDTO(false, "支付宝通知参数为空", null, null);
        }
        AlipayConfig config = effectiveConfig(repository.config());
        if (!gatewayService.verifyNotify(config, params)) {
            return new AlipayNotifyResultDTO(false, "支付宝通知验签失败", params.get("out_trade_no"), params.get("trade_no"));
        }
        String outTradeNo = requireText(params.get("out_trade_no"), "商户订单号不能为空");
        String tradeNo = trimToNull(params.get("trade_no"));
        String tradeStatus = trimToNull(params.get("trade_status"));
        if (!TRADE_SUCCESS.equals(tradeStatus) && !TRADE_FINISHED.equals(tradeStatus)) {
            return new AlipayNotifyResultDTO(true, "忽略未完成交易状态：" + tradeStatus, outTradeNo, tradeNo);
        }
        AlipayRechargeOrder order = repository.findOrder(outTradeNo)
                .orElseThrow(() -> new IllegalArgumentException("充值订单不存在：" + outTradeNo));
        if (order.status() == AlipayOrderStatus.PAID) {
            return new AlipayNotifyResultDTO(true, "订单已入账", outTradeNo, tradeNo);
        }
        verifyAmount(order, params.get("total_amount"));
        PluginWalletTransaction walletTransaction = walletService.credit(new PluginWalletChangeRequest(
                order.userId(),
                order.assetCode(),
                order.walletAmount(),
                "alipay:" + outTradeNo,
                "支付宝充值：" + outTradeNo
        ));
        repository.saveOrder(order.markPaid(tradeNo, walletTransaction.id()));
        return new AlipayNotifyResultDTO(true, "success", outTradeNo, tradeNo);
    }

    public Optional<AlipayRechargeOrder> findOrder(String outTradeNo) {
        return repository.findOrder(outTradeNo);
    }

    public List<AlipayRechargeOrder> listOrders(int page, int size) {
        return repository.listOrders(page, size);
    }

    private AlipayConfig effectiveConfig(AlipayConfig config) {
        AlipayConfig normalized = config == null ? AlipayConfig.defaults() : config.normalized();
        return new AlipayConfig(
                normalized.appId(),
                normalized.privateKey(),
                normalized.alipayPublicKey(),
                normalized.gatewayUrl(),
                effectiveCallbackUrl(normalized.notifyUrl(), NOTIFY_PATH),
                effectiveCallbackUrl(normalized.returnUrl(), RETURN_PATH),
                normalized.signType(),
                normalized.charset(),
                normalized.enabled()
        ).normalized();
    }

    private AlipayConfig storageConfig(AlipayConfig config) {
        AlipayConfig normalized = config == null ? AlipayConfig.defaults() : config.normalized();
        return new AlipayConfig(
                normalized.appId(),
                normalized.privateKey(),
                normalized.alipayPublicKey(),
                normalized.gatewayUrl(),
                storageCallbackUrl(normalized.notifyUrl(), NOTIFY_PATH),
                storageCallbackUrl(normalized.returnUrl(), RETURN_PATH),
                normalized.signType(),
                normalized.charset(),
                normalized.enabled()
        ).normalized();
    }

    private String effectiveCallbackUrl(String configuredUrl, String path) {
        String defaultUrl = defaultCallbackUrl(path);
        if (!hasText(defaultUrl)) {
            return configuredUrl;
        }
        if (!hasText(configuredUrl) || isDefaultCallbackUrl(configuredUrl, path, defaultUrl)) {
            return defaultUrl;
        }
        return configuredUrl.trim();
    }

    private String storageCallbackUrl(String configuredUrl, String path) {
        String defaultUrl = defaultCallbackUrl(path);
        if (!hasText(configuredUrl) || isDefaultCallbackUrl(configuredUrl, path, defaultUrl)) {
            return null;
        }
        return configuredUrl.trim();
    }

    private String defaultCallbackUrl(String path) {
        return publicWebUrl()
                .map(baseUrl -> baseUrl + path)
                .orElse(null);
    }

    private Optional<String> publicWebUrl() {
        return frameworkServices.setting(APP_WEB_URL_SETTING)
                .or(() -> frameworkServices.setting(APP_BASE_URL_SETTING))
                .map(this::stripTrailingSlash)
                .filter(this::hasText);
    }

    private boolean isDefaultCallbackUrl(String value, String path, String defaultUrl) {
        String text = value == null ? "" : value.trim();
        return text.equals(path) || text.equals(defaultUrl);
    }

    private String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private void ensureMoneyAsset(String assetCode) {
        PluginWalletAsset asset = walletService.findAsset(assetCode)
                .orElseThrow(() -> new IllegalArgumentException("钱包资产不存在：" + assetCode));
        if (!asset.money()) {
            throw new IllegalArgumentException("支付宝充值只能入账货币类资产");
        }
        if (!asset.enabled()) {
            throw new IllegalArgumentException("钱包资产已停用：" + assetCode);
        }
    }

    private void verifyAmount(AlipayRechargeOrder order, String totalAmount) {
        if (totalAmount == null || totalAmount.isBlank()) {
            throw new IllegalArgumentException("支付宝通知金额为空");
        }
        BigDecimal notified = new BigDecimal(totalAmount).setScale(2);
        if (notified.compareTo(order.amount()) != 0) {
            throw new IllegalArgumentException("支付宝通知金额与订单金额不一致");
        }
    }

    private String ownerId(String requestUserId, Long currentUserId) {
        if (requestUserId != null && !requestUserId.isBlank()) {
            return requestUserId.trim();
        }
        if (currentUserId == null) {
            throw new IllegalArgumentException("用户不能为空");
        }
        return String.valueOf(currentUserId);
    }

    private String assetCode(String assetCode) {
        String value = trimToNull(assetCode);
        return value == null ? PluginWalletService.MONEY_ASSET_CODE : value.toUpperCase(Locale.ROOT);
    }

    private AlipayProductType productType(String productType) {
        if (productType == null || productType.isBlank()) {
            return SUPPORTED_PRODUCT_TYPE;
        }
        AlipayProductType value;
        try {
            value = AlipayProductType.valueOf(productType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的支付宝支付产品：" + productType);
        }
        if (value != SUPPORTED_PRODUCT_TYPE) {
            throw new IllegalArgumentException("支付宝充值当前仅支持电脑网页支付");
        }
        return value;
    }

    private String payloadType(AlipayProductType productType) {
        return switch (productType) {
            case FACE_TO_FACE -> "QRCODE";
            case APP -> "ORDER_STRING";
            case PAGE, WAP -> "HTML_FORM";
        };
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
}
