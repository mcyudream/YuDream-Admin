package online.yudream.base.interfaces.common.config;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.interfaces.common.interceptor.WebInvokeTimeInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 请求日志拦截器注册配置。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(WebLogProperties.class)
public class WebLogConfigure implements WebMvcConfigurer {

    private final WebLogProperties properties;

    public WebLogConfigure(WebLogProperties properties) {
        this.properties = properties;
        log.info("WebInvokeTimeInterceptor 配置加载: enabled={}, prefix={}", properties.isEnabled(), properties.getPrefix());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!properties.isEnabled()) {
            log.warn("WebInvokeTimeInterceptor 已关闭，不会记录请求日志");
            return;
        }
        registry.addInterceptor(new WebInvokeTimeInterceptor(properties))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/error",
                        "/static/**",
                        "/webjars/**",
                        "/favicon.ico"
                );
        log.info("WebInvokeTimeInterceptor 已注册");
    }
}
