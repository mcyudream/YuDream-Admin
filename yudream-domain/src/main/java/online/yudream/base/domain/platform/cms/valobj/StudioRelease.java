package online.yudream.base.domain.platform.cms.valobj;

import java.time.Instant;

/** 不可变发布文档；恢复仅复制其内容到草稿。 */
public record StudioRelease(String id, long revision, String documentJson, Instant createdAt) {}
