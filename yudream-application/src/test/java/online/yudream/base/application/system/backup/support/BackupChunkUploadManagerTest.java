package online.yudream.base.application.system.backup.support;

import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupChunkUploadManagerTest {

    private final BackupChunkUploadManager manager = new BackupChunkUploadManager(
            new BackupDirectorySupport("target/test-chunk-upload"));

    @Test
    void sequentialChunksAssembleAndVerify() throws Exception {
        String uploadId = manager.begin("backup.zip", 6);
        manager.write(uploadId, 0, stream("abc"));
        manager.write(uploadId, 3, stream("def"));
        manager.finish(uploadId, 6, sha256Hex("abcdef"));

        java.nio.file.Path staged = manager.stagedFile(uploadId);
        assertEquals(6, java.nio.file.Files.size(staged));
        manager.consume(uploadId);
    }

    @Test
    void offsetGapRejected() {
        String uploadId = manager.begin("backup.zip", 6);
        manager.write(uploadId, 0, stream("abc"));
        assertThrows(BizException.class, () -> manager.write(uploadId, 2, stream("de")));
    }

    @Test
    void sizeMismatchAbortsSession() throws Exception {
        String uploadId = manager.begin("backup.zip", 6);
        manager.write(uploadId, 0, stream("abcdef"));
        assertThrows(BizException.class, () -> manager.finish(uploadId, 999, null));
        // 已作废：无法再取暂存文件
        assertThrows(BizException.class, () -> manager.stagedFile(uploadId));
    }

    @Test
    void shaMismatchAbortsSession() throws Exception {
        String uploadId = manager.begin("backup.zip", 6);
        manager.write(uploadId, 0, stream("abcdef"));
        assertThrows(BizException.class, () -> manager.finish(uploadId, 6, "deadbeef"));
    }

    @Test
    void abortedSessionIsGone() throws Exception {
        String uploadId = manager.begin("backup.zip", 3);
        manager.abort(uploadId);
        assertThrows(BizException.class, () -> manager.write(uploadId, 0, stream("abc")));
    }

    private static ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String text) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
