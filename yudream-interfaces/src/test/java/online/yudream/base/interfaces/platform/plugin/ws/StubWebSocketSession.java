package online.yudream.base.interfaces.platform.plugin.ws;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试用 WebSocketSession 替身：记录发送与关闭动作。
 */
public class StubWebSocketSession implements WebSocketSession {

    public final Map<String, Object> attributes = new java.util.concurrent.ConcurrentHashMap<>();
    public final List<String> sentTexts = new CopyOnWriteArrayList<>();
    public final List<byte[]> sentBinaries = new CopyOnWriteArrayList<>();
    public final List<CloseStatus> closedWith = new CopyOnWriteArrayList<>();
    private volatile boolean open = true;

    @Override
    public String getId() {
        return "stub-" + System.identityHashCode(this);
    }

    @Override
    public URI getUri() {
        return null;
    }

    @Override
    public org.springframework.http.HttpHeaders getHandshakeHeaders() {
        return new org.springframework.http.HttpHeaders();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return null;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return null;
    }

    @Override
    public String getAcceptedProtocol() {
        return "";
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
    }

    @Override
    public int getTextMessageSizeLimit() {
        return 1024 * 1024;
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
    }

    @Override
    public int getBinaryMessageSizeLimit() {
        return 1024 * 1024;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) throws IOException {
        if (!open) {
            throw new IOException("session closed");
        }
        if (message instanceof org.springframework.web.socket.BinaryMessage binary) {
            java.nio.ByteBuffer buffer = binary.getPayload();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            sentBinaries.add(data);
        } else {
            sentTexts.add(String.valueOf(message.getPayload()));
        }
    }

    @Override
    public List<org.springframework.web.socket.WebSocketExtension> getExtensions() {
        return List.of();
    }

    @Override
    public void close() {
        close(CloseStatus.NORMAL);
    }

    @Override
    public void close(CloseStatus status) {
        open = false;
        closedWith.add(status);
    }
}
