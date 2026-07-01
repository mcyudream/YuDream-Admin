package online.yudream.base.bootstrap.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器配置。
 * <p>
 * 注册 {@link SaInterceptor}，开启注解式鉴权功能（如 {@code @SaCheckPermission}）。
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，拦截所有路径
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }
}
