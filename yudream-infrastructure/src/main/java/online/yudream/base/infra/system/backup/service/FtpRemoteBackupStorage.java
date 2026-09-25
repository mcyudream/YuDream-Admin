package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.backup.aggregate.RemoteTarget;
import online.yudream.base.domain.system.backup.enumerate.RemoteTargetType;
import online.yudream.base.domain.system.backup.service.RemoteBackupStorage;
import online.yudream.base.domain.system.backup.valobj.RemoteEntry;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * FTP/FTPS 异地备份存储。连接为短生命周期：每次操作建连、用毕断开，不持长驻资源。
 */
public class FtpRemoteBackupStorage implements RemoteBackupStorage {

    private static final int TIMEOUT_MILLIS = 60_000;

    private final RemoteTarget target;

    FtpRemoteBackupStorage(RemoteTarget target) {
        this.target = target;
    }

    @Override
    public void test() {
        FTPClient client = connect();
        try {
            String dir = target.getBasePath();
            if (!client.changeWorkingDirectory(dir)) {
                throw new BizException("连接成功，但远端目录不存在或不可进入：" + dir);
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException("FTP 探测失败：" + e.getMessage());
        } finally {
            disconnectQuietly(client);
        }
    }

    @Override
    public void put(String path, InputStream in, long size) {
        FTPClient client = connect();
        try {
            String remotePath = remotePath(path);
            ensureRemoteDirs(client, remotePath);
            OutputStream out = client.storeFileStream(remotePath);
            if (out == null) {
                throw new BizException("远端无法创建备份文件：" + path + "（" + client.getReplyString() + "）");
            }
            try (out) {
                in.transferTo(out);
            }
            if (!client.completePendingCommand()) {
                throw new BizException("远端写入未正常完成：" + path);
            }
        } catch (IOException e) {
            throw new BizException("FTP 上传失败：" + e.getMessage());
        } finally {
            disconnectQuietly(client);
        }
    }

    @Override
    public List<RemoteEntry> list(String prefix) {
        FTPClient client = connect();
        try {
            FTPFile[] files = client.listFiles(target.getBasePath());
            if (files == null) {
                throw new BizException("远端目录不可读：" + target.getBasePath());
            }
            List<RemoteEntry> entries = new ArrayList<>();
            for (FTPFile file : files) {
                if (file.isDirectory()) {
                    continue;
                }
                if (prefix != null && !prefix.isBlank() && !file.getName().startsWith(prefix)) {
                    continue;
                }
                entries.add(new RemoteEntry(file.getName(), file.getSize(),
                        file.getTimestamp() == null ? null :
                                file.getTimestamp().getTimeInMillis()));
            }
            return entries;
        } catch (IOException e) {
            throw new BizException("FTP 列表失败：" + e.getMessage());
        } finally {
            disconnectQuietly(client);
        }
    }

    @Override
    public void fetch(String path, Path destination) {
        FTPClient client = connect();
        try {
            InputStream in = client.retrieveFileStream(remotePath(path));
            if (in == null) {
                throw new BizException("远端备份文件不存在：" + path);
            }
            try (in) {
                Files.copy(in, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (!client.completePendingCommand()) {
                throw new BizException("远端下载未正常完成：" + path);
            }
        } catch (IOException e) {
            throw new BizException("FTP 下载失败：" + e.getMessage());
        } finally {
            disconnectQuietly(client);
        }
    }

    @Override
    public void delete(String path) {
        FTPClient client = connect();
        try {
            if (!client.deleteFile(remotePath(path))) {
                throw new BizException("远端删除失败：" + path + "（" + client.getReplyString() + "）");
            }
        } catch (IOException e) {
            throw new BizException("FTP 删除失败：" + e.getMessage());
        } finally {
            disconnectQuietly(client);
        }
    }

    private FTPClient connect() {
        FTPClient client = target.getType() == RemoteTargetType.FTPS ? new FTPSClient() : new FTPClient();
        client.setControlEncoding("UTF-8");
        client.setConnectTimeout(TIMEOUT_MILLIS);
        client.setDefaultTimeout(TIMEOUT_MILLIS);
        try {
            client.connect(target.getHost(), target.getPort());
            if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
                client.disconnect();
                throw new BizException("无法连接备份服务器：" + target.getHost() + ":" + target.getPort());
            }
            if (!client.login(target.getUsername() == null ? "anonymous" : target.getUsername(),
                    target.getPassword() == null ? "" : target.getPassword())) {
                throw new BizException("备份服务器登录失败，请检查账号与密码");
            }
            if (target.getType() == RemoteTargetType.FTPS) {
                try {
                    ((FTPSClient) client).execPBSZ(0);
                    ((FTPSClient) client).execPROT("P");
                } catch (IOException ignored) {
                    // 服务器不支持 PBSZ/PROT 时按默认策略继续
                }
            }
            client.enterLocalPassiveMode();
            client.setFileType(FTP.BINARY_FILE_TYPE);
            client.setControlKeepAliveTimeout(60);
            return client;
        } catch (BizException e) {
            disconnectQuietly(client);
            throw e;
        } catch (IOException e) {
            disconnectQuietly(client);
            throw new BizException("FTP 连接失败：" + e.getMessage());
        }
    }

    private void disconnectQuietly(FTPClient client) {
        if (client.isConnected()) {
            try {
                client.logout();
            } catch (IOException ignored) {
                // logout 失败直接断开
            }
            try {
                client.disconnect();
            } catch (IOException ignored) {
                // 忽略断开异常
            }
        }
    }

    private String remotePath(String path) {
        String base = target.getBasePath();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        return base + normalized;
    }

    private void ensureRemoteDirs(FTPClient client, String remotePath) throws IOException {
        int split = remotePath.lastIndexOf('/');
        if (split <= 0) {
            return;
        }
        String[] segments = remotePath.substring(1, split).split("/");
        StringBuilder current = new StringBuilder();
        for (String segment : segments) {
            current.append('/').append(segment);
            client.makeDirectory(current.toString());
        }
    }
}
