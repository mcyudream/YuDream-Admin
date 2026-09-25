package online.yudream.base.application.system.backup.support;

import online.yudream.base.domain.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 备份目录支撑：本机归档与临时文件统一落在配置目录（默认 config/backup，
 * Docker 卷挂载 config 时天然持久化），并提供安静删除。
 */
@Component
public class BackupDirectorySupport {

    private final Path directory;

    public BackupDirectorySupport(@Value("${yudream.system.backup.directory:config/backup}") String directory) {
        this.directory = Path.of(directory);
    }

    public Path ensureDirectory() {
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException e) {
            throw new BizException("备份目录不可用：" + directory + "（" + e.getMessage() + "）");
        }
    }

    /** 在备份目录创建临时归档文件。 */
    public Path newTempFile(String prefix) {
        ensureDirectory();
        try {
            return Files.createTempFile(directory, prefix, ".zip");
        } catch (IOException e) {
            throw new BizException("创建备份临时文件失败：" + e.getMessage());
        }
    }

    /** 在备份目录创建正式归档文件（重名时先清理）。 */
    public Path newArchiveFile(String archiveName) {
        ensureDirectory();
        Path file = directory.resolve(archiveName);
        deleteQuietly(file);
        return file;
    }

    public void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 临时文件删除失败不影响主流程
        }
    }
}
