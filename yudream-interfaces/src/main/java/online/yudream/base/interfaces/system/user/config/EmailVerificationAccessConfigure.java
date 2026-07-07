package online.yudream.base.interfaces.system.user.config;

import lombok.RequiredArgsConstructor;
import online.yudream.base.interfaces.system.user.interceptor.EmailVerificationAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class EmailVerificationAccessConfigure implements WebMvcConfigurer {

    private final EmailVerificationAccessInterceptor emailVerificationAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(emailVerificationAccessInterceptor)
                .addPathPatterns("/api/**");
    }
}
