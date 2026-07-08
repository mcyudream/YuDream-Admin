package online.yudream.base.interfaces.platform.plugin.config;

import lombok.RequiredArgsConstructor;
import online.yudream.base.interfaces.platform.plugin.interceptor.AuthlibAliInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AuthlibAliConfigure implements WebMvcConfigurer {

    private final AuthlibAliInterceptor authlibAliInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authlibAliInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/error");
    }
}
