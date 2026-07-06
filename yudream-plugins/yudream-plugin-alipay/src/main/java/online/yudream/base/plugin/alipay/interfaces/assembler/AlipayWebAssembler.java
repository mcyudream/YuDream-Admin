package online.yudream.base.plugin.alipay.interfaces.assembler;

import online.yudream.base.plugin.alipay.application.cmd.AlipayConfigSaveCmd;
import online.yudream.base.plugin.alipay.application.cmd.AlipayRechargeCreateCmd;
import online.yudream.base.plugin.alipay.application.dto.AlipayNotifyResultDTO;
import online.yudream.base.plugin.alipay.application.dto.AlipayRechargeCreateDTO;
import online.yudream.base.plugin.alipay.domain.aggregate.AlipayRechargeOrder;
import online.yudream.base.plugin.alipay.domain.valobj.AlipayConfig;
import online.yudream.base.plugin.alipay.interfaces.request.AlipayConfigSaveRequest;
import online.yudream.base.plugin.alipay.interfaces.request.AlipayRechargeCreateRequest;
import online.yudream.base.plugin.alipay.interfaces.res.AlipayConfigRes;
import online.yudream.base.plugin.alipay.interfaces.res.AlipayNotifyResultRes;
import online.yudream.base.plugin.alipay.interfaces.res.AlipayRechargeCreateRes;
import online.yudream.base.plugin.alipay.interfaces.res.AlipayRechargeOrderRes;

public class AlipayWebAssembler {

    public AlipayConfigSaveCmd toCmd(AlipayConfigSaveRequest request) {
        return new AlipayConfigSaveCmd(
                request.appId(),
                request.privateKey(),
                request.alipayPublicKey(),
                request.gatewayUrl(),
                request.notifyUrl(),
                request.returnUrl(),
                request.signType(),
                request.charset(),
                request.enabled()
        );
    }

    public AlipayRechargeCreateCmd toCmd(AlipayRechargeCreateRequest request) {
        return new AlipayRechargeCreateCmd(
                request.userId(),
                request.assetCode(),
                request.amount(),
                request.walletAmount(),
                request.subject(),
                request.body(),
                request.productType()
        );
    }

    public AlipayConfigRes toRes(AlipayConfig config) {
        return new AlipayConfigRes(
                config.appId(),
                config.privateKey(),
                config.alipayPublicKey(),
                config.gatewayUrl(),
                config.notifyUrl(),
                config.returnUrl(),
                config.signType(),
                config.charset(),
                config.enabled()
        );
    }

    public AlipayRechargeCreateRes toRes(AlipayRechargeCreateDTO dto) {
        return new AlipayRechargeCreateRes(toRes(dto.order()), dto.payPayload());
    }

    public AlipayRechargeOrderRes toRes(AlipayRechargeOrder order) {
        return new AlipayRechargeOrderRes(
                order.outTradeNo(),
                order.userId(),
                order.assetCode(),
                order.amount(),
                order.walletAmount(),
                order.subject(),
                order.body(),
                order.productType().name(),
                order.status().name(),
                order.tradeNo(),
                order.walletTransactionId(),
                order.createdAt(),
                order.updatedAt(),
                order.paidAt()
        );
    }

    public AlipayNotifyResultRes toRes(AlipayNotifyResultDTO dto) {
        return new AlipayNotifyResultRes(dto.success(), dto.message(), dto.outTradeNo(), dto.tradeNo());
    }
}
