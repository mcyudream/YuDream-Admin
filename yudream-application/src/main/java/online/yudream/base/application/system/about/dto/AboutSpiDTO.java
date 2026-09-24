package online.yudream.base.application.system.about.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前装载的插件 SPI 版本：来自 SPI JAR 内 spi-build.properties，读取失败时 version 为空。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AboutSpiDTO {

    private String version;
    private String artifact;
    private String buildTime;
}
