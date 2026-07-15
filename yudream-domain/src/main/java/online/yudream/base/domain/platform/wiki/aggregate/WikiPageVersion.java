package online.yudream.base.domain.platform.wiki.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIndexStatus;

import java.time.LocalDateTime;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
public class WikiPageVersion extends BaseDomain {
    private Long spaceId; private Long nodeId; private long revision; private String title; private String markdown;
    private String contentHash; private WikiIndexStatus indexStatus; private String indexError; private LocalDateTime indexedAt;
    public void indexing() { indexStatus = WikiIndexStatus.INDEXING; indexError = null; }
    public void indexed() { indexStatus = WikiIndexStatus.READY; indexError = null; indexedAt = LocalDateTime.now(); }
    public void failed(String error) { indexStatus = WikiIndexStatus.FAILED; indexError = error == null ? "索引失败" : error; }
}
