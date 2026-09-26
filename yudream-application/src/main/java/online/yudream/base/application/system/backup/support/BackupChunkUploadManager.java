package online.yudream.base.application.system.backup.support;

import online.yudream.base.domain.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 备份归档分片上传会话管理：分片按偏移顺序追加到备份目录临时文件，
 * 边写边算 SHA-256，finish 时校验总大小与摘要后转为「已暂存」状态，
 * 供分析/合并导入按 uploadId 消费。会话超时（2 小时无活动）在下次 begin 时机会性清理。
 */
@Component
public class BackupChunkUploadManager {

    /** 单分片上限（前端按 8MB 切片）。 */
    public static final long MAX_CHUNK_BYTES = 16L * 1024 * 1024;
    private static final long SESSION_TTL_MILLIS = 2 * 60 * 60 * 1000L;

    private final BackupDirectorySupport directories;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public BackupChunkUploadManager(BackupDirectorySupport directories) {
        this.directories = directories;
    }

    /** 开启新会话并创建暂存文件。 */
    public String begin(String name, long size) {
        sweepStale();
        if (size < 0) {
            throw new BizException("归档大小无效");
        }
        String uploadId = "up-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Path file = directories.newTempFile("chunk-");
        sessions.put(uploadId, new Session(file, name == null ? "" : name, size,
                MessageDigestHolder.sha256(), 0L, false, System.currentTimeMillis()));
        return uploadId;
    }

    /** 追加一个分片：偏移必须等于当前已接收长度（顺序上传）。 */
    public synchronized long write(String uploadId, long offset, InputStream data) {
        Session session = activeSession(uploadId);
        if (session.complete()) {
            throw new BizException("该上传已完成，不能再追加分片");
        }
        if (offset != session.received()) {
            throw new BizException("分片偏移不连续：期望 " + session.received() + " 实际 " + offset);
        }
        byte[] buffer = new byte[64 * 1024];
        long written = 0;
        try (InputStream input = data;
             var output = Files.newOutputStream(session.file(), StandardOpenOption.APPEND)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                output.write(buffer, 0, read);
                session.digest().update(buffer, 0, read);
                written += read;
            }
        } catch (IOException e) {
            throw new BizException("分片写入失败：" + e.getMessage());
        }
        Session updated = session.withReceived(session.received() + written);
        sessions.put(uploadId, updated);
        return updated.received();
    }

    /** 结束上传：校验总大小与 SHA-256，标记为已暂存。返回暂存文件路径。 */
    public Path finish(String uploadId, long expectedSize, String expectedSha256) {
        Session session = activeSession(uploadId);
        if (session.expectedSize() >= 0 && session.expectedSize() != expectedSize) {
            abort(uploadId);
            throw new BizException("分片合并后大小与声明不一致：期望 " + session.expectedSize()
                    + " 实际 " + expectedSize + "，会话已作废，请重新上传");
        }
        if (session.received() != expectedSize) {
            throw new BizException("分片未传完：已接收 " + session.received() + "/" + expectedSize);
        }
        String actual = HexFormat.of().formatHex(session.digest().digest());
        if (expectedSha256 != null && !expectedSha256.isBlank()
                && !actual.equalsIgnoreCase(expectedSha256.trim())) {
            abort(uploadId);
            throw new BizException("分片合并后 SHA-256 校验失败，会话已作废，请重新上传");
        }
        sessions.put(uploadId, session.withComplete(true));
        return session.file();
    }

    /** 取已暂存归档路径（必须已完成）。 */
    public Path stagedFile(String uploadId) {
        Session session = activeSession(uploadId);
        if (!session.complete()) {
            throw new BizException("该上传尚未完成分片合并");
        }
        return session.file();
    }

    /** 消费暂存归档（分析/导入接管文件后调用）：移除会话。 */
    public void consume(String uploadId) {
        sessions.remove(uploadId);
    }

    /** 中止并清理暂存文件。 */
    public void abort(String uploadId) {
        Session session = sessions.remove(uploadId);
        if (session != null) {
            directories.deleteQuietly(session.file());
        }
    }

    private Session activeSession(String uploadId) {
        Session session = uploadId == null ? null : sessions.get(uploadId);
        if (session == null) {
            throw new BizException("上传会话不存在或已过期，请重新上传");
        }
        return session;
    }

    private void sweepStale() {
        long deadline = System.currentTimeMillis() - SESSION_TTL_MILLIS;
        sessions.values().removeIf(session -> {
            if (session.lastActivity() >= deadline) {
                return false;
            }
            directories.deleteQuietly(session.file());
            return true;
        });
    }

    private record Session(Path file, String name, long expectedSize, MessageDigest digest,
                           long received, boolean complete, long lastActivity) {

        private Session withReceived(long received) {
            return new Session(file, name, expectedSize, digest, received, complete,
                    System.currentTimeMillis());
        }

        private Session withComplete(boolean complete) {
            return new Session(file, name, expectedSize, digest, received, complete,
                    System.currentTimeMillis());
        }
    }

    private static final class MessageDigestHolder {

        private MessageDigestHolder() {
        }

        static MessageDigest sha256() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new UncheckedIOException(new IOException("SHA-256 不可用", e));
            }
        }
    }
}
