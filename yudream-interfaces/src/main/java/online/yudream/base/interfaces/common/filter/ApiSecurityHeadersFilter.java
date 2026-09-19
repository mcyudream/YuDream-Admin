package online.yudream.base.interfaces.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API 响应安全头：nosniff 阻止 MIME 嗅探（文件内容端点尤其重要），
 * frame-ancestors 由前端站点按需控制，这里对 API 一律拒绝被嵌入。
 */
@Component
public class ApiSecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // nosniff 重复下发无害；frame 选项统一由边缘（nginx）设置，避免多值头失效
        response.setHeader("X-Content-Type-Options", "nosniff");
        chain.doFilter(request, response);
    }
}
