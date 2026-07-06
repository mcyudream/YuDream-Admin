package online.yudream.base.plugin.alipay.infrastructure.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeAppPayModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeAppPayResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.alipay.api.internal.util.AlipaySignature;
import online.yudream.base.plugin.alipay.domain.aggregate.AlipayRechargeOrder;
import online.yudream.base.plugin.alipay.domain.enumerate.AlipayProductType;
import online.yudream.base.plugin.alipay.domain.valobj.AlipayConfig;

import java.util.Map;

public class AlipayGatewayService {

    private static final String FORMAT = "json";

    public String createPayPayload(AlipayConfig config, AlipayRechargeOrder order) {
        return switch (order.productType()) {
            case APP -> createAppPayOrder(config, order);
            case PAGE -> createPagePayForm(config, order);
            case WAP -> createWapPayForm(config, order);
            case FACE_TO_FACE -> createQrCode(config, order);
        };
    }

    public String createAppPayOrder(AlipayConfig config, AlipayRechargeOrder order) {
        config.ensureUsable();
        AlipayTradeAppPayRequest request = new AlipayTradeAppPayRequest();
        request.setNotifyUrl(config.notifyUrl());
        request.setReturnUrl(config.returnUrl());

        AlipayTradeAppPayModel model = new AlipayTradeAppPayModel();
        model.setOutTradeNo(order.outTradeNo());
        model.setTotalAmount(order.amount().toPlainString());
        model.setSubject(order.subject());
        model.setBody(order.body());
        model.setProductCode("QUICK_MSECURITY_PAY");
        request.setBizModel(model);

        try {
            AlipayTradeAppPayResponse response = client(config).sdkExecute(request);
            if (!response.isSuccess()) {
                throw new IllegalArgumentException("支付宝下单失败：" + response.getSubMsg());
            }
            return response.getBody();
        } catch (AlipayApiException e) {
            throw new IllegalArgumentException("支付宝下单失败：" + e.getErrMsg(), e);
        }
    }

    public String createPagePayForm(AlipayConfig config, AlipayRechargeOrder order) {
        config.ensureUsable();
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(config.notifyUrl());
        request.setReturnUrl(config.returnUrl());

        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(order.outTradeNo());
        model.setTotalAmount(order.amount().toPlainString());
        model.setSubject(order.subject());
        model.setBody(order.body());
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        request.setBizModel(model);

        try {
            AlipayTradePagePayResponse response = client(config).pageExecute(request);
            if (!response.isSuccess()) {
                throw new IllegalArgumentException("支付宝下单失败：" + response.getSubMsg());
            }
            return response.getBody();
        } catch (AlipayApiException e) {
            throw new IllegalArgumentException("支付宝下单失败：" + e.getErrMsg(), e);
        }
    }

    public String createWapPayForm(AlipayConfig config, AlipayRechargeOrder order) {
        config.ensureUsable();
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        request.setNotifyUrl(config.notifyUrl());
        request.setReturnUrl(config.returnUrl());

        AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
        model.setOutTradeNo(order.outTradeNo());
        model.setTotalAmount(order.amount().toPlainString());
        model.setSubject(order.subject());
        model.setBody(order.body());
        model.setProductCode("QUICK_WAP_WAY");
        request.setBizModel(model);

        try {
            AlipayTradeWapPayResponse response = client(config).pageExecute(request);
            if (!response.isSuccess()) {
                throw new IllegalArgumentException("支付宝下单失败：" + response.getSubMsg());
            }
            return response.getBody();
        } catch (AlipayApiException e) {
            throw new IllegalArgumentException("支付宝下单失败：" + e.getErrMsg(), e);
        }
    }

    public String createQrCode(AlipayConfig config, AlipayRechargeOrder order) {
        config.ensureUsable();
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(config.notifyUrl());

        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        model.setOutTradeNo(order.outTradeNo());
        model.setTotalAmount(order.amount().toPlainString());
        model.setSubject(order.subject());
        model.setBody(order.body());
        request.setBizModel(model);

        try {
            AlipayTradePrecreateResponse response = client(config).execute(request);
            if (!response.isSuccess()) {
                throw new IllegalArgumentException("支付宝下单失败：" + response.getSubMsg());
            }
            return response.getQrCode();
        } catch (AlipayApiException e) {
            throw new IllegalArgumentException("支付宝下单失败：" + e.getErrMsg(), e);
        }
    }

    public boolean verifyNotify(AlipayConfig config, Map<String, String> params) {
        config.ensureUsable();
        try {
            return AlipaySignature.rsaCheckV1(params, config.alipayPublicKey(), config.charset(), config.signType());
        } catch (AlipayApiException e) {
            throw new IllegalArgumentException("支付宝通知验签失败：" + e.getErrMsg(), e);
        }
    }

    private AlipayClient client(AlipayConfig config) {
        return new DefaultAlipayClient(
                config.gatewayUrl(),
                config.appId(),
                config.privateKey(),
                FORMAT,
                config.charset(),
                config.alipayPublicKey(),
                config.signType()
        );
    }
}
