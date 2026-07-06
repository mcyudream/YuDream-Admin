package online.yudream.base.plugin.spi.system.payment;

public interface PluginPaymentChannel {

    PluginPaymentChannelInfo info();

    PluginPaymentCreateResult createRecharge(PluginPaymentCreateRequest request);
}
