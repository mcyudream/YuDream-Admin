package online.yudream.base.infra.platform.render.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.render.model.RenderModels.RenderRequest;
import online.yudream.base.domain.platform.render.model.RenderModels.RenderedImage;
import online.yudream.base.domain.platform.render.service.MessageRenderGateway;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HttpMessageRenderGateway implements MessageRenderGateway {
    private final MessageRenderProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public RenderedImage render(RenderRequest request) {
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new BizException("渲染内容不能为空");
        }
        JsonNode response = client().post()
                .uri("/v1/render/" + request.sourceType().name().toLowerCase())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload(request))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(timeout());
        if (response == null || !response.hasNonNull("data")) {
            throw new BizException("渲染服务未返回图片数据");
        }
        try {
            return new RenderedImage(
                    response.path("contentType").asText(MediaType.IMAGE_PNG_VALUE),
                    Base64.getDecoder().decode(response.path("data").asText()),
                    response.path("width").asInt(),
                    response.path("height").asInt());
        } catch (IllegalArgumentException ex) {
            throw new BizException("渲染服务返回的数据格式无效");
        }
    }

    @Override
    public boolean healthy() {
        try {
            return Boolean.TRUE.equals(client().get().uri("/health").retrieve()
                    .bodyToMono(JsonNode.class).map(node -> node.path("ok").asBoolean(false)).block(timeout()));
        } catch (Exception ignored) {
            return false;
        }
    }

    private WebClient client() {
        URI base;
        try {
            base = URI.create(properties.getBaseUrl());
        } catch (IllegalArgumentException ex) {
            throw new BizException("渲染服务地址无效");
        }
        WebClient.Builder builder = WebClient.builder().baseUrl(base.toString());
        if (StringUtils.hasText(properties.getToken())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getToken().trim());
        }
        return builder.build();
    }

    private Map<String, Object> payload(RenderRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", request.content());
        if (request.width() != null) payload.put("width", request.width());
        if (request.maxHeight() != null) payload.put("maxHeight", request.maxHeight());
        if (request.transparent() != null) payload.put("transparent", request.transparent());
        if (StringUtils.hasText(request.format())) payload.put("format", request.format());
        if (request.options() != null && !request.options().isEmpty()) payload.put("options", request.options());
        return payload;
    }

    private Duration timeout() {
        return properties.getTimeout() == null ? Duration.ofSeconds(30) : properties.getTimeout();
    }
}
