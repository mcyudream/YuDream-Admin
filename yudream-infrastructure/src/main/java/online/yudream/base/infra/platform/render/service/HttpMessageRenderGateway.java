package online.yudream.base.infra.platform.render.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.render.model.RenderModels.RenderRequest;
import online.yudream.base.domain.platform.render.model.RenderModels.RenderedImage;
import online.yudream.base.domain.platform.render.model.RenderModels.SourceType;
import online.yudream.base.domain.platform.render.service.MessageRenderGateway;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
        HttpRenderResponse response;
        try {
            response = client().post()
                    .uri("/v1/render/" + request.sourceType().name().toLowerCase())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload(request))
                    .exchangeToMono(value -> value.bodyToMono(byte[].class)
                            .defaultIfEmpty(new byte[0])
                            .map(body -> new HttpRenderResponse(
                                    value.statusCode().value(),
                                    value.headers().contentType().map(MediaType::toString).orElse(""),
                                    body
                            )))
                    .block(timeout());
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BizException("渲染服务不可用：" + exception.getMessage());
        }
        return decodeResponse(response, objectMapper);
    }

    static RenderedImage decodeResponse(HttpRenderResponse response, ObjectMapper objectMapper) {
        if (response == null) throw new BizException("渲染服务未返回响应");
        if (response.status() < 200 || response.status() >= 300) {
            throw new BizException("渲染服务请求失败（HTTP " + response.status() + "）："
                    + responseMessage(response.body(), objectMapper));
        }
        if (response.contentType().toLowerCase().startsWith("image/")) {
            if (response.body().length == 0) throw new BizException("渲染服务返回了空图片");
            int[] dimensions = dimensions(response.body());
            return new RenderedImage(response.contentType().split(";")[0], response.body(), dimensions[0], dimensions[1]);
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode payload = root.path("data").isObject() ? root.path("data") : root;
            String encoded = firstText(
                    payload.path("data").asText(""),
                    payload.path("base64").asText(""),
                    payload.path("image").asText(""),
                    root.path("data").isTextual() ? root.path("data").asText("") : ""
            );
            int comma = encoded.indexOf(',');
            if (encoded.startsWith("data:image/") && comma > 0) encoded = encoded.substring(comma + 1);
            if (!StringUtils.hasText(encoded)) {
                throw new BizException("渲染服务返回成功状态但没有图片数据：" + responseMessage(response.body(), objectMapper));
            }
            return new RenderedImage(
                    firstText(payload.path("contentType").asText(""), root.path("contentType").asText(""), MediaType.IMAGE_PNG_VALUE),
                    Base64.getDecoder().decode(encoded),
                    payload.path("width").asInt(root.path("width").asInt()),
                    payload.path("height").asInt(root.path("height").asInt()));
        } catch (BizException exception) {
            throw exception;
        } catch (IllegalArgumentException ex) {
            throw new BizException("渲染服务返回的数据格式无效");
        } catch (Exception ex) {
            throw new BizException("渲染服务返回成功状态但响应无法解析：" + responseMessage(response.body(), objectMapper));
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
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(base.toString())
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxResponseBytes()));
        if (StringUtils.hasText(properties.getToken())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getToken().trim());
        }
        return builder.build();
    }

    private int maxResponseBytes() {
        long configured = properties.getMaxResponseSize() == null
                ? 16L * 1024 * 1024
                : properties.getMaxResponseSize().toBytes();
        if (configured < 1024 * 1024) configured = 1024 * 1024;
        return (int) Math.min(configured, 64L * 1024 * 1024);
    }

    private Map<String, Object> payload(RenderRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        switch (request.sourceType()) {
            case HTML -> payload.put("html", request.content());
            case MARKDOWN -> payload.put("markdown", request.content());
            case URL -> payload.put("url", request.content());
        }
        if (request.width() != null) payload.put("width", request.width());
        if (request.maxHeight() != null) payload.put("maxHeight", request.maxHeight());
        if (request.transparent() != null) payload.put("transparent", request.transparent());
        if (StringUtils.hasText(request.format())) payload.put("format", request.format());
        if (request.options() != null && !request.options().isEmpty()) payload.put("options", request.options());
        if (request.sourceType() == SourceType.HTML) {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("sanitize", false);
            options.put("allowStyles", true);
            if (request.options() != null) {
                options.putAll(request.options());
                Object selector = request.options().get("selector");
                if (selector instanceof String value && !value.isBlank()) payload.put("selector", value);
            }
            payload.put("options", options);
        }
        return payload;
    }

    private static String responseMessage(byte[] responseBody, ObjectMapper objectMapper) {
        String text = responseBody == null ? "" : new String(responseBody, StandardCharsets.UTF_8).trim();
        try {
            JsonNode body = objectMapper.readTree(text);
            return firstText(body.path("message").asText(""), body.path("error").asText(""),
                    body.path("data").path("message").asText(""), limit(text));
        } catch (Exception ignored) {
            return text.isBlank() ? "渲染服务未提供错误详情" : limit(text);
        }
    }

    private static String firstText(String... values) {
        for (String value : values) if (StringUtils.hasText(value)) return value.trim();
        return "";
    }

    private static String limit(String value) {
        return value.length() <= 256 ? value : value.substring(0, 256);
    }

    private static int[] dimensions(byte[] image) {
        try {
            var value = ImageIO.read(new ByteArrayInputStream(image));
            return value == null ? new int[]{0, 0} : new int[]{value.getWidth(), value.getHeight()};
        } catch (Exception ignored) {
            return new int[]{0, 0};
        }
    }

    record HttpRenderResponse(int status, String contentType, byte[] body) {
        HttpRenderResponse {
            contentType = contentType == null ? "" : contentType;
            body = body == null ? new byte[0] : body;
        }
    }

    private Duration timeout() {
        return properties.getTimeout() == null ? Duration.ofSeconds(30) : properties.getTimeout();
    }
}
