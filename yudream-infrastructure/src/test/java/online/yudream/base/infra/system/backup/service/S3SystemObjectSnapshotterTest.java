package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.system.integration.valobj.ObjectStorageConfig;
import online.yudream.base.infra.system.integration.StorageClientProvider;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3SystemObjectSnapshotterTest {

    @Test
    void closesEveryStreamEvenWhenConsumerDoesNotReadIt() {
        Fixture fixture = new Fixture();
        List<String> keys = new ArrayList<>();
        fixture.snapshotter.streamObjects(object -> keys.add(object.objectKey()));
        assertEquals(List.of("textures/one", "textures/two"), keys);
        assertEquals(2, fixture.streams.size());
        assertTrue(fixture.streams.stream().allMatch(stream -> stream.closed));
    }

    @Test
    void closesStreamWhenConsumerThrows() {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("archive failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> fixture.snapshotter.streamObjects(object -> { throw failure; })));
        assertEquals(1, fixture.streams.size());
        assertTrue(fixture.streams.getFirst().closed);
    }

    @Test
    void propagatesCloseFailure() {
        Fixture fixture = new Fixture();
        fixture.closeFails = true;
        assertThrows(UncheckedIOException.class, () -> fixture.snapshotter.streamObjects(object -> {}));
        assertTrue(fixture.streams.getFirst().closed);
    }

    private static class Fixture {
        final List<TrackingStream> streams = new ArrayList<>();
        boolean closeFails;
        final S3Client client = (S3Client) Proxy.newProxyInstance(S3Client.class.getClassLoader(),
                new Class<?>[]{S3Client.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "listObjectsV2Paginator" -> new ListObjectsV2Iterable((S3Client) proxy,
                            (ListObjectsV2Request) args[0]);
                    case "listObjectsV2" -> ListObjectsV2Response.builder().isTruncated(false).contents(
                            S3Object.builder().key("textures/").size(0L).build(),
                            S3Object.builder().key("textures/one").size(3L).build(),
                            S3Object.builder().key("textures/two").size(3L).build()).build();
                    case "getObject" -> {
                        assertEquals("test-bucket", ((GetObjectRequest) args[0]).bucket());
                        TrackingStream stream = new TrackingStream(closeFails);
                        streams.add(stream);
                        yield new ResponseInputStream<>(GetObjectResponse.builder()
                                .contentLength(3L).contentType("image/png").build(), stream);
                    }
                    case "serviceName" -> "s3";
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        final S3SystemObjectSnapshotter snapshotter = new S3SystemObjectSnapshotter(new StorageClientProvider(null) {
            @Override
            public ObjectStorageConfig config() {
                return new ObjectStorageConfig("", "", "", "test-bucket", "us-east-1", true);
            }

            @Override
            public S3Client client() {
                return client;
            }
        });
    }

    private static class TrackingStream extends ByteArrayInputStream {
        boolean closed;
        final boolean closeFails;

        TrackingStream(boolean closeFails) {
            super(new byte[]{1, 2, 3});
            this.closeFails = closeFails;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (closeFails) {
                throw new IOException("close failed");
            }
            super.close();
        }
    }
}
