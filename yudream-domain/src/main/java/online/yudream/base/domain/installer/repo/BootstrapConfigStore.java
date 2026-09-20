package online.yudream.base.domain.installer.repo;

import online.yudream.base.domain.installer.valobj.BootstrapConfig;

import java.nio.file.Path;

/**
 * 引导配置存储端口：安装向导把数据库/Redis 连接与部署级密钥写入独立引导文件
 * （默认 {@code config/yudream-bootstrap.properties}），重启后生效。
 */
public interface BootstrapConfigStore {

    /**
     * 引导配置文件是否已存在（存在即视为已完成安装）。
     */
    boolean exists();

    /**
     * 引导配置文件的落盘位置。
     */
    Path location();

    /**
     * 原子写入引导配置；失败不得破坏既有文件。
     */
    void save(BootstrapConfig config);
}
