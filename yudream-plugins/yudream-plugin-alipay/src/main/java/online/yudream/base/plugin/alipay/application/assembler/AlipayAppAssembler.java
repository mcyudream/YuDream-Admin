package online.yudream.base.plugin.alipay.application.assembler;

import online.yudream.base.plugin.alipay.application.cmd.AlipayConfigSaveCmd;
import online.yudream.base.plugin.alipay.domain.valobj.AlipayConfig;

public class AlipayAppAssembler {

    public AlipayConfig merge(AlipayConfig existing, AlipayConfigSaveCmd cmd) {
        AlipayConfig current = existing == null ? AlipayConfig.defaults() : existing;
        return new AlipayConfig(
                value(cmd.appId(), current.appId()),
                value(cmd.privateKey(), current.privateKey()),
                value(cmd.alipayPublicKey(), current.alipayPublicKey()),
                value(cmd.gatewayUrl(), current.gatewayUrl()),
                value(cmd.notifyUrl(), current.notifyUrl()),
                value(cmd.returnUrl(), current.returnUrl()),
                value(cmd.signType(), current.signType()),
                value(cmd.charset(), current.charset()),
                cmd.enabled() == null ? current.enabled() : cmd.enabled()
        ).normalized();
    }

    private String value(String input, String fallback) {
        if (input != null && input.contains("******")) {
            return fallback;
        }
        return input == null ? fallback : input;
    }
}
