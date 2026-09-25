package online.yudream.base.domain.system.backup.valobj;

/** 异地存储中的一个远端文件条目。 */
public record RemoteEntry(String name, long size, Long modifiedAtMillis) {
}
