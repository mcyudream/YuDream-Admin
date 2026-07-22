package online.yudream.base.infra.platform.render.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.render.model.RenderModels.RenderRequest;
import online.yudream.base.domain.platform.render.model.RenderModels.SourceType;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpMessageRenderGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsSuccessfulRawImageResponse() throws Exception {
        BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(source, "png", output);

        var rendered = HttpMessageRenderGateway.decodeResponse(
                new HttpMessageRenderGateway.HttpRenderResponse(200, "image/png", output.toByteArray()), objectMapper
        );

        assertEquals("image/png", rendered.contentType());
        assertEquals(3, rendered.width());
        assertEquals(2, rendered.height());
        assertArrayEquals(output.toByteArray(), rendered.content());
    }

    @Test
    void acceptsNestedJsonBase64Response() {
        byte[] image = new byte[]{1, 2, 3};
        String json = "{\"data\":{\"base64\":\"" + Base64.getEncoder().encodeToString(image)
                + "\",\"contentType\":\"image/png\",\"width\":10,\"height\":20}}";

        var rendered = HttpMessageRenderGateway.decodeResponse(
                new HttpMessageRenderGateway.HttpRenderResponse(200, "application/json", json.getBytes(StandardCharsets.UTF_8)), objectMapper
        );

        assertArrayEquals(image, rendered.content());
        assertEquals(10, rendered.width());
        assertEquals(20, rendered.height());
    }

    @Test
    void acceptsCurrentRenderServiceTopLevelJsonResponse() {
        byte[] image = new byte[]{4, 5, 6};
        String json = "{\"contentType\":\"image/png\",\"data\":\""
                + Base64.getEncoder().encodeToString(image) + "\",\"width\":320,\"height\":67}";

        var rendered = HttpMessageRenderGateway.decodeResponse(
                new HttpMessageRenderGateway.HttpRenderResponse(200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8)), objectMapper
        );

        assertArrayEquals(image, rendered.content());
        assertEquals("image/png", rendered.contentType());
        assertEquals(320, rendered.width());
        assertEquals(67, rendered.height());
    }

    @Test
    void decodesJsonResponseLargerThanWebClientDefaultBuffer() {
        byte[] image = new byte[300_000];
        for (int index = 0; index < image.length; index++) image[index] = (byte) (index % 251);
        String json = "{\"contentType\":\"image/png\",\"data\":\""
                + Base64.getEncoder().encodeToString(image) + "\",\"width\":760,\"height\":1200}";
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length > 262_144);

        var rendered = HttpMessageRenderGateway.decodeResponse(
                new HttpMessageRenderGateway.HttpRenderResponse(200, "application/json",
                        json.getBytes(StandardCharsets.UTF_8)), objectMapper
        );

        assertArrayEquals(image, rendered.content());
        assertEquals(760, rendered.width());
        assertEquals(1200, rendered.height());
    }

    @Test
    void webClientAcceptsResponseLargerThanDefault256KbLimit() {
        byte[] image = new byte[300_000];
        String json = "{\"contentType\":\"image/png\",\"data\":\""
                + Base64.getEncoder().encodeToString(image) + "\",\"width\":760,\"height\":1200}";
        var server = HttpServer.create().port(0).route(routes -> routes.post("/v1/render/html",
                (request, response) -> response.header("Content-Type", "application/json")
                        .sendString(Mono.just(json)))).bindNow();
        try {
            MessageRenderProperties properties = new MessageRenderProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.port());
            HttpMessageRenderGateway gateway = new HttpMessageRenderGateway(properties, objectMapper);

            var rendered = gateway.render(new RenderRequest(SourceType.HTML, "<article>card</article>",
                    null, null, null, "png", Map.of()));

            assertArrayEquals(image, rendered.content());
            assertEquals(760, rendered.width());
            assertEquals(1200, rendered.height());
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void reportsBodyWhenSuccessfulResponseContainsNoImage() {
        BizException error = assertThrows(BizException.class, () -> HttpMessageRenderGateway.decodeResponse(
                new HttpMessageRenderGateway.HttpRenderResponse(200, "application/json",
                        "{\"message\":\"selector did not match\"}".getBytes(StandardCharsets.UTF_8)), objectMapper
        ));

        assertTrue(error.getMessage().contains("selector did not match"));
    }
}
