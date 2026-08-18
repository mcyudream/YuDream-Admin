package online.yudream.base.application.platform.wiki.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WikiSearchHitDTO {
    private double score;
    private String nodeId;
    private String sourceId;
    private String kind;
    private String title;
    private String path;
    private String content;
    private String sourceUrl;
    private String spaceSlug;
    private String spaceName;
    /** 命中页面正文引用的站内图片（问答/检索可直接展示） */
    private List<Image> images;

    @Data
    @Builder
    public static class Image {
        private String url;
        private String caption;
    }
}
