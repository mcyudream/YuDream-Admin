package online.yudream.base.interfaces.platform.wiki.res;

import lombok.Builder;

import java.util.List;

@Builder
public record WikiChatResultRes(
        String answer,
        String reasoning,
        List<Citation> citations
) {
    @Builder
    public record Citation(String title, String path, String nodeId, String excerpt, List<Image> images) {

        @Builder
        public record Image(String url, String caption) {
        }
    }
}
