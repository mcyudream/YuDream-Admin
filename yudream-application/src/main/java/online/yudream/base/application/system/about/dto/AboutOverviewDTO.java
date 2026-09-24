package online.yudream.base.application.system.about.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关于系统总览：框架与 SPI 当前版本、插件装载统计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AboutOverviewDTO {

    private AboutFrameworkDTO framework;
    private AboutSpiDTO spi;
    private int pluginTotal;
    private int pluginEnabled;
    private int pluginError;
}
