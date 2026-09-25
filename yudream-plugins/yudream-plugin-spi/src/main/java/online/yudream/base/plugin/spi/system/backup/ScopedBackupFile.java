package online.yudream.base.plugin.spi.system.backup;

/**
 * 插件备份范围内的单个文件条目。
 *
 * @param path   范围内部相对路径
 * @param size   字节数
 * @param sha256 内容 SHA-256（十六进制小写，可能为空）
 * @since 2.33.0
 */
public record ScopedBackupFile(String path, long size, String sha256) {
}
