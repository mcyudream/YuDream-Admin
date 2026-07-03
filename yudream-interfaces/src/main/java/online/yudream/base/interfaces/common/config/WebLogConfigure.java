package online.yudream.base.interfaces.common.config;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.monitor.service.SystemMonitorAppService;
import online.yudream.base.interfaces.common.interceptor.WebInvokeTimeInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
@EnableConfigurationProperties(WebLogProperties.class)
public class WebLogConfigure implements WebMvcConfigurer {

    private final WebLogProperties properties;
    private final SystemMonitorAppService systemMonitorAppService;

    public WebLogConfigure(WebLogProperties properties, SystemMonitorAppService systemMonitorAppService) {
        this.properties = properties;
        this.systemMonitorAppService = systemMonitorAppService;
        log.info("WebInvokeTimeInterceptor loaded: enabled={}, prefix={}", properties.isEnabled(), properties.getPrefix());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!properties.isEnabled()) {
            log.warn("WebInvokeTimeInterceptor disabled");
            return;
        }
        registry.addInterceptor(new WebInvokeTimeInterceptor(properties, systemMonitorAppService))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/error",
                        "/static/**",
                        "/webjars/**",
                        "/favicon.ico"
                );
        log.info("WebInvokeTimeInterceptor registered");
    }
}
