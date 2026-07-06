package online.yudream.base.plugin.spi.system.payment;

import java.util.List;

public record PluginPaymentChannelInfo(
        String code,
        String name,
        String icon,
        String description,
        boolean enabled,
        List<String> productTypes
) {
}
