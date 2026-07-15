package online.yudream.base.domain.platform.wiki.valobj;
public record WikiSearchHit(double score, Long nodeId, String title, String path, String content) { }
