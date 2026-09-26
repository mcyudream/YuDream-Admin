package online.yudream.base.infra.system.log.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerSocketLogReaderTest {

    @Test
    void drainsMultiplexedFramesIntoLines() throws Exception {
        byte[] body = concat(
                frame((byte) 1, "hello\n"),
                frame((byte) 2, "wor"),
                frame((byte) 2, "ld\n"));
        List<String> lines = new ArrayList<>();
        DockerSocketLogReader.drainMultiplexed(new ByteArrayInputStream(body), lines::add);
        assertEquals(List.of("hello", "world"), lines);
    }

    @Test
    void skipsHttpHeadersAndLeavesBody() throws Exception {
        byte[] headers = "HTTP/1.1 200 OK\r\nContent-Type: application/vnd.docker.multiplexed-stream\r\n\r\n"
                .getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[]{1, 2, 3, 4};
        ByteArrayInputStream in = new ByteArrayInputStream(concat(headers, body));
        DockerSocketLogReader.skipHttpHeaders(in);
        assertEquals(1, in.read());
        assertEquals(2, in.read());
        assertEquals(3, in.read());
        assertEquals(4, in.read());
    }

    @Test
    void logRequestUsesHttp10SoDockerSkipsChunkedEncoding() {
        String request = DockerSocketLogReader.logRequest("swustmc-frontend", 200);
        assertTrue(request.startsWith("GET /v1.41/containers/swustmc-frontend/logs?follow=true&stdout=true&stderr=true&tail=200&timestamps=true"),
                request);
        assertTrue(request.endsWith(" HTTP/1.0\r\nHost: docker\r\nConnection: close\r\n\r\n"), request);
    }

    @Test
    void rejectsErrorStatusInsteadOfParsingBodyAsFrames() {
        byte[] response = "HTTP/1.1 404 Not Found\r\nContent-Type: application/json\r\n\r\n{\"message\":\"No such container\"}"
                .getBytes(StandardCharsets.UTF_8);
        IOException error = assertThrows(IOException.class,
                () -> DockerSocketLogReader.skipHttpHeaders(new ByteArrayInputStream(response)));
        assertTrue(error.getMessage().contains("404"), error.getMessage());
    }

    @Test
    void rejectsNonHttpPayload() {
        byte[] response = "docker daemon gone\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class,
                () -> DockerSocketLogReader.skipHttpHeaders(new ByteArrayInputStream(response)));
    }

    private static byte[] frame(byte streamType, String payload) {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[8 + data.length];
        frame[0] = streamType;
        frame[4] = (byte) (data.length >>> 24);
        frame[5] = (byte) (data.length >>> 16);
        frame[6] = (byte) (data.length >>> 8);
        frame[7] = (byte) data.length;
        System.arraycopy(data, 0, frame, 8, data.length);
        return frame;
    }

    private static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            out.writeBytes(array);
        }
        return out.toByteArray();
    }
}
