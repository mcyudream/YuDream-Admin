package online.yudream.base.application.platform.wiki.dto;

import lombok.Builder;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;

import java.util.List;

@Builder
public record WikiChatResultDTO(
        String answer,
        String reasoning,
        List<Citation> citations,
        AiUsage usage
) {

    public WikiChatResultDTO {
        reasoning = reasoning == null ? "" : reasoning;
        usage = usage == null ? AiUsage.empty() : usage;
    }

    public WikiChatResultDTO(String answer, List<Citation> citations) {
        this(answer, "", citations, AiUsage.empty());
    }

    public WikiChatResultDTO(String answer, String reasoning, List<Citation> citations) {
        this(answer, reasoning, citations, AiUsage.empty());
    }

    @Builder
    public record Citation(String title, String path, String nodeId, String excerpt, List<Image> images) {

        @Builder
        public record Image(String url, String caption) {
        }
    }
}
