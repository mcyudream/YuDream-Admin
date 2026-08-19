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
        /** Markdown 图片原始 alt 文本；caption 保留为兼容字段。 */
        private String alt;
        /** 原始资料抽取阶段由视觉模型生成的事实性 caption。 */
        private String generatedCaption;
        /** 兼容旧消费方：优先为 alt，缺失时回退到 AI 生成 caption。 */
        private String caption;
    }
}
