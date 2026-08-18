package online.yudream.base.infra.platform.wiki.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiExtractionStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSourceFormat;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSourceKind;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("platformWikiSource")
public class WikiSourceDO extends BaseDO {
    @Indexed
    private Long spaceId;
    private String folderPath;
    private String fileName;
    private String title;
    private WikiSourceKind kind;
    private String url;
    private String mimeType;
    private WikiSourceFormat format;
    private Long fileObjectId;
    @Indexed
    private String contentHash;
    private String extractedText;
    private WikiExtractionStatus extractionStatus;
    private String extractionError;
    private List<WikiSourceImageDO> images = new ArrayList<>();
    private WikiIngestStatus ingestStatus;
    private String ingestHash;
    private String ingestError;
    private LocalDateTime ingestedAt;
    private int sort;
}
