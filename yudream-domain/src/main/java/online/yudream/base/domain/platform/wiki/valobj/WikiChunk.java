package online.yudream.base.domain.platform.wiki.valobj;
public record WikiChunk(Long spaceId, Long nodeId, Long versionId, int sequence, String title, String path, String content) { }
