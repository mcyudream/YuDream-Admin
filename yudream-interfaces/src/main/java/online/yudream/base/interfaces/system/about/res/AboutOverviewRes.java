package online.yudream.base.interfaces.system.about.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关于系统总览视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AboutOverviewRes {

    private AboutFrameworkRes framework;
    private AboutSpiRes spi;
    private int pluginTotal;
    private int pluginEnabled;
    private int pluginError;
}
