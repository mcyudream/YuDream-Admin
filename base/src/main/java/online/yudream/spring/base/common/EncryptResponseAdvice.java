package online.yudream.spring.base.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import online.yudream.spring.base.utils.AESUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class EncryptResponseAdvice implements ResponseBodyAdvice<Object> {

    @Resource
    private AESUtil aesUtil;

    @Value("${base.security.enable-res-encode:false}") // 默认关闭
    private boolean enableResEncode;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 只拦截 JSON
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {

        // 如果没有开启加密，直接返回原始 body
        if (!enableResEncode) {
            return body;
        }
        try {
            String originalJson = new ObjectMapper().writeValueAsString(body);
            String encryptedData = aesUtil.encrypt(originalJson);
            return new R.EncryptedResponse(encryptedData);
        } catch (Exception e) {
            throw new RuntimeException("响应加密失败", e);
        }
    }
}
