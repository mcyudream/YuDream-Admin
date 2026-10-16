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
    /** 命中页面正文引用的站内图片中，与查询相关且证据分达标的证据图片 */
    private List<Image> images;
    /** 存在被省略的图片证据时的服务端说明（低相关/低置信/匿名不可读）；无省略时为 null */
    private String imageNotice;

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
        /** 站内文件 ID（字符串，保持长 ID 精度）。 */
        private String imageId;
        /** 图片证据与查询的相关度（0-1，服务端按 alt/caption 命中计算）。 */
        private double score;
        /** 启发式证据分（0-1），按 caption 来源与状态评估；不是模型校准概率，AI caption 0.8、人工 alt 0.7。 */
        private double confidence;
        /** 证据分来源说明：当前恒为 HEURISTIC（启发式，非校准概率）。 */
        private String confidenceBasis;
        /** 图片所属原始资料 ID（字符串；无归属时为 null）。 */
        private String sourceId;
        /** 页码（从 1 开始；无页码语义的资料为 0，仅用于定位，不表示版面坐标）。 */
        private int pageNumber;
        /** 同资料内的图序（从 0 开始）。 */
        private int sequence;
    }
}
