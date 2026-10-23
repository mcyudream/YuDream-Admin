package online.yudream.base.interfaces.platform.preview.controller;

import online.yudream.base.application.platform.preview.service.PluginPreviewFileOpener;
import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PublicFilePreviewControllerTest {

    @Test
    void invalidRangesCloseTheOpenedStreamWithoutReading() throws Exception {
        for (String range : new String[]{"bytes=20-", "bytes=3-1", "bytes=invalid"}) {
            TrackedInputStream input = new TrackedInputStream();
            var response = controller(input).file("token", "/test.txt", range);

            assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, response.getStatusCode());
            assertEquals("bytes */4", response.getHeaders().getFirst("Content-Range"));
            assertTrue(input.closed);
            assertEquals(4, input.available());
            response.getBody().writeTo(new ByteArrayOutputStream());
        }
    }

    @Test
    void validRangeKeepsStreamOpenUntilBodyRunsAndClosesAfterPartialRead() throws Exception {
        TrackedInputStream input = new TrackedInputStream();
        var response = controller(input).file("token", "/test.txt", "bytes=1-2");
        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertFalse(input.closed);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        assertEquals("bc", output.toString(StandardCharsets.UTF_8));
        assertEquals(1, input.available());
        assertTrue(input.closed);
    }

    @Test
    void clientWriteFailureClosesTheOpenedStream() throws Exception {
        TrackedInputStream input = new TrackedInputStream();
        var response = controller(input).file("token", "/test.txt", null);
        OutputStream disconnected = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("客户端断开");
            }
        };

        assertThrows(IOException.class, () -> response.getBody().writeTo(disconnected));
        assertTrue(input.closed);
    }

    private static PublicFilePreviewController controller(TrackedInputStream input) {
        PluginPreviewFileOpener opener = token -> Optional.of(
                new PluginStoredFile("test.txt", "text/plain", 4L, input));
        return new PublicFilePreviewController(opener);
    }

    private static class TrackedInputStream extends ByteArrayInputStream {
        boolean closed;

        TrackedInputStream() {
            super("abcd".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
