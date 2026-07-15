package online.yudream.base.application.platform.wiki.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record WikiIndexSnapshotDTO(List<Chunk> chunks, List<Relation> relations) {
    @Builder public record Chunk(int sequence, String title, String path, String content) { }
    @Builder public record Relation(String source, String sourceType, String relation, String target, String targetType, double confidence) { }
}
