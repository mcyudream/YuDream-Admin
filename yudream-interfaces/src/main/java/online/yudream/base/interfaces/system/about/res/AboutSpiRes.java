package online.yudream.base.interfaces.system.about.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前装载的插件 SPI 版本视图；version 为空表示运行时未读取到（老版本 SPI 或未过滤资源）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AboutSpiRes {

    private String version;
    private String artifact;
    private String buildTime;
}
