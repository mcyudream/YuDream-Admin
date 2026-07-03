package online.yudream.base.interfaces.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web 请求日志配置。
 */
@Data
@ConfigurationProperties(prefix = "yudream.web.log")
public class WebLogProperties {

    /** 是否启用请求耗时日志 */
    private boolean enabled = true;

    /** 日志前缀，会显示在 [前缀] 中 */
    private String prefix = "YuDreamAdmin";
}
