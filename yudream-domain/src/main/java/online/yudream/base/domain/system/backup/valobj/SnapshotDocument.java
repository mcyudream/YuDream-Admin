package online.yudream.base.domain.system.backup.valobj;

/**
 * 快照文档：id 为规范化字符串（ObjectId 十六进制 / 数字 / 字符串原样），
 * ejson 为 EJSON EXTENDED 单行 JSON，可无损往返（Date/Long/Binary/ObjectId 类型保真）。
 */
public record SnapshotDocument(String id, String ejson) {
}
