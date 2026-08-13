package online.yudream.base.infra.system.log.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnixDomainSocketAddress;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 通过 Docker Engine HTTP API 读取容器日志（基于 unix socket，纯 JDK 实现，无需 docker CLI）。
 * 适用于后端运行在 Linux 容器内、挂载了 /var/run/docker.sock 的生产环境。
 */
final class DockerSocketLogReader {

    private DockerSocketLogReader() {
    }

    /**
     * 打开到 docker daemon 的 unix socket，流式读取指定容器日志（follow 模式）。
     * logs 端点返回 application/vnd.docker.multiplexed-stream：8 字节帧头 + 负载的多路复用流。
     */
    static void stream(String socketPath, String container, long tail, Consumer<String> onLine) throws IOException {
        try (SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socketPath))) {
            OutputStream out = Channels.newOutputStream(channel);
            String request = "GET /v1.41/containers/" + encode(container)
                    + "/logs?follow=true&stdout=true&stderr=true&tail=" + tail + "&timestamps=true HTTP/1.1\r\n"
                    + "Host: docker\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = Channels.newInputStream(channel);
            skipHttpHeaders(in);
            drainMultiplexed(in, onLine);
        }
    }

    /**
     * 解析多路复用流：每个帧为 [1 字节流类型][3 字节填充][4 字节大端长度][负载]，按行回调。
     * 帧边界与行边界无关，需跨帧累积到换行符才输出一行。
     */
    static void drainMultiplexed(InputStream in, Consumer<String> onLine) throws IOException {
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
        while (true) {
            byte[] header = in.readNBytes(8);
            if (header.length < 8) {
                break;
            }
            int size = ((header[4] & 0xFF) << 24) | ((header[5] & 0xFF) << 16)
                    | ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
            if (size <= 0) {
                continue;
            }
            byte[] payload = in.readNBytes(size);
            for (byte value : payload) {
                if (value == '\n') {
                    String line = lineBuffer.toString(StandardCharsets.UTF_8);
                    lineBuffer.reset();
                    if (!line.isBlank()) {
                        onLine.accept(line);
                    }
                } else {
                    lineBuffer.write(value);
                }
            }
        }
    }

    /** 跳过 HTTP 响应头，直到出现 "\r\n\r\n"，之后即为多路复用帧体。 */
    static void skipHttpHeaders(InputStream in) throws IOException {
        int state = 0;
        while (state < 4) {
            int value = in.read();
            if (value < 0) {
                return;
            }
            char expected = (state % 2 == 0) ? '\r' : '\n';
            state = (value == expected) ? state + 1 : (value == '\r' ? 1 : 0);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
