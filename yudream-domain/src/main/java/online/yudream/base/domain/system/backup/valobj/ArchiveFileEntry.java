package online.yudream.base.domain.system.backup.valobj;

/** 归档内单个文件条目（对象存储文件与插件范围文件通用）。 */
public record ArchiveFileEntry(String path, long size, String sha256) {
}
