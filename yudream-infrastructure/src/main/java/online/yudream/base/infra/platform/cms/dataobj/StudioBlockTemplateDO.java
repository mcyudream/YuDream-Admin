package online.yudream.base.infra.platform.cms.dataobj;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** 用户区块模板，独立集合。 */
@Data
@Document("cms_studio_block_template")
public class StudioBlockTemplateDO {
    @Id
    private String id;
    private String name;
    private String category;
    private String description;
    private List<String> keywords;
    private String nodesJson;
    private long revision;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
