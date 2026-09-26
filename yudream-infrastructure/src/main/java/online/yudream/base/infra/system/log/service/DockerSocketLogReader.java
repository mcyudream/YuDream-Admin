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
            out.write(logRequest(container, tail).getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = Channels.newInputStream(channel);
            skipHttpHeaders(in);
            drainMultiplexed(in, onLine);
        }
    }

    /**
     * 构造日志流请求，必须用 HTTP/1.0：HTTP/1.1 下守护进程对 follow 日志流返回
     * Transfer-Encoding: chunked，帧体混入的分块头（如 "200\r\n"）会被当成帧头解析出
     * 上亿字节的假帧长；HTTP/1.0 禁止 chunked，帧体以连接关闭（EOF）界定，可直接逐帧读取。
     */
    static String logRequest(String container, long tail) {
        return "GET /v1.41/containers/" + encode(container)
                + "/logs?follow=true&stdout=true&stderr=true&tail=" + tail + "&timestamps=true HTTP/1.0\r\n"
                + "Host: docker\r\n"
                + "Connection: close\r\n\r\n";
    }

    /**
     * 解析多路复用流：每个帧为 [1 字节流类型][3 字节填充][4 字节大端长度][负载]，按行回调。
     * 帧边界与行边界无关，需跨帧累积到换行符才输出一行。
     */
    /** 单行缓冲上限：超限先输出截断行，防止容器输出无换行流导致堆内存耗尽。 */
    private static final int MAX_LINE_BYTES = 16 * 1024;
    /** 单帧长度上限：Docker 守护进程帧有界，异常大帧视为流损坏并中止。 */
    private static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;

    static void drainMultiplexed(InputStream in, Consumer<String> onLine) throws IOException {
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
        boolean lineTruncated = false;
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
            if (size > MAX_FRAME_BYTES) {
                throw new IOException("Docker log frame too large: " + size);
            }
            byte[] payload = in.readNBytes(size);
            for (byte value : payload) {
                if (value == '\n') {
                    String line = lineBuffer.toString(StandardCharsets.UTF_8);
                    lineBuffer.reset();
                    if (lineTruncated) {
                        line = line + "…[truncated]";
                        lineTruncated = false;
                    }
                    if (!line.isBlank()) {
                        onLine.accept(line);
                    }
                } else {
                    if (lineBuffer.size() >= MAX_LINE_BYTES) {
                        // 超出部分丢弃仅保留截断标记，行边界到达后恢复
                        lineTruncated = true;
                        continue;
                    }
                    lineBuffer.write(value);
                }
            }
        }
    }

    /** 校验响应状态行并跳过 HTTP 响应头，直到出现 "\r\n\r\n"，之后即为多路复用帧体。 */
    static void skipHttpHeaders(InputStream in) throws IOException {
        String statusLine = readLine(in);
        if (statusLine == null || !statusLine.startsWith("HTTP/") || statusLine.length() < 12) {
            throw new IOException("Docker 日志接口响应异常：" + statusLine);
        }
        if (statusLine.charAt(9) != '2') {
            // 容器不存在等真实错误返回 JSON 体，若继续按帧解析只会报出误导性的"帧过大"
            throw new IOException("Docker 日志接口返回异常状态：" + statusLine);
        }
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

    private static String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = in.read()) >= 0 && value != '\n') {
            line.append((char) value);
        }
        int length = line.length();
        if (length > 0 && line.charAt(length - 1) == '\r') {
            line.setLength(length - 1);
        }
        return value < 0 && line.isEmpty() ? null : line.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
