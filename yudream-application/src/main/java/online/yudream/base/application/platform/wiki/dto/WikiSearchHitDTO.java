package online.yudream.base.application.platform.wiki.dto;
import lombok.Builder; import lombok.Data;
@Data @Builder public class WikiSearchHitDTO { private double score; private String nodeId; private String title; private String path; private String content; private String sourceUrl; }
