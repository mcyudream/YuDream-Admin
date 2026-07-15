package online.yudream.base.domain.platform.wiki.valobj;

public record WikiGraphRelation(String source, String sourceType, String relation, String target, String targetType, double confidence) { }
