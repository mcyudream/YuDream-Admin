package online.yudream.base.interfaces.system.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.dto.ApiEncryptedPayloadDTO;
import online.yudream.base.application.system.security.service.ApiEncryptionAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.common.ResultCode;
import online.yudream.base.interfaces.system.security.assembler.ApiSecurityWebAssembler;
import online.yudream.base.interfaces.system.security.request.ApiEncryptedPayloadRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class ApiPayloadEncryptionFilter extends OncePerRequestFilter {

    public static final String ENCRYPTED_HEADER = "X-Api-Encrypted";
    public static final String ENCRYPTED_KEY_HEADER = "X-Api-Encrypted-Key";
    public static final String ENCRYPTED_IV_HEADER = "X-Api-Encrypted-Iv";

    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String STATUS_PATH = "/api/system/security/encryption/status";
    private static final String PUBLIC_KEY_PATH = "/api/system/security/encryption/public-key";

    private final ApiEncryptionAppService apiEncryptionAppService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!shouldFilter(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!apiEncryptionAppService.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!encryptedRequest(request)) {
            writeBadRequest(response, "接口加密已开启，请使用加密请求");
            return;
        }
        byte[] sessionKey;
        HttpServletRequest nextRequest;
        try {
            sessionKey = apiEncryptionAppService.decryptSessionKey(request.getHeader(ENCRYPTED_KEY_HEADER));
            nextRequest = decryptRequestIfNeeded(request, sessionKey);
        }
        catch (BizException ex) {
            writeBadRequest(response, ex.getMessage());
            return;
        }
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(nextRequest, wrappedResponse);
        encryptResponse(wrappedResponse, sessionKey);
    }

    private boolean shouldFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (STATUS_PATH.equals(path) || PUBLIC_KEY_PATH.equals(path)) {
            return false;
        }
        if (path != null && path.startsWith("/api/public/cms")) {
            return false;
        }
        if (path != null && (path.startsWith("/api/system/excel") || path.startsWith("/api/system/files"))) {
            return false;
        }
        return path != null && path.startsWith("/api/") && acceptsJson(request);
    }

    private boolean acceptsJson(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (!StringUtils.hasText(contentType)) {
            return true;
        }
        return contentType.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private boolean encryptedRequest(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader(ENCRYPTED_HEADER))
                && StringUtils.hasText(request.getHeader(ENCRYPTED_KEY_HEADER));
    }

    private HttpServletRequest decryptRequestIfNeeded(HttpServletRequest request, byte[] sessionKey) throws IOException {
        if (!BODY_METHODS.contains(request.getMethod().toUpperCase())) {
            return request;
        }
        String iv = request.getHeader(ENCRYPTED_IV_HEADER);
        if (!StringUtils.hasText(iv)) {
            return request;
        }
        ApiEncryptedPayloadRequest payload = objectMapper.readValue(request.getInputStream(), ApiEncryptedPayloadRequest.class);
        if (!StringUtils.hasText(payload.getData())) {
            return request;
        }
        String plainBody = apiEncryptionAppService.decrypt(sessionKey, iv, payload.getData());
        return new ApiEncryptedRequestWrapper(request, plainBody);
    }

    private void encryptResponse(ContentCachingResponseWrapper response, byte[] sessionKey) throws IOException {
        byte[] body = response.getContentAsByteArray();
        if (body.length == 0) {
            response.copyBodyToResponse();
            return;
        }
        String plain = new String(body, response.getCharacterEncoding() == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(response.getCharacterEncoding()));
        ApiEncryptedPayloadDTO encrypted = apiEncryptionAppService.encrypt(sessionKey, plain);
        byte[] encryptedBody = objectMapper.writeValueAsBytes(ApiSecurityWebAssembler.toRes(encrypted));
        response.resetBuffer();
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(ENCRYPTED_HEADER, "true");
        response.getOutputStream().write(encryptedBody);
        response.copyBodyToResponse();
    }

    private void writeBadRequest(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(ResultCode.BAD_REQUEST.getCode(), message));
    }
}
