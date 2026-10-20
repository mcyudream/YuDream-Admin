package online.yudream.base.infra.platform.cms.dataobj;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.List;

/** 独立集合，不读取或覆盖旧CMS页面/文件。 */
@Data
@Document("cms_studio_workspace")
public class StudioWorkspaceDO {
    @Id
    private String themeCode;
    private long revision;
    private String documentJson;
    private String publishedReleaseId;
    private Instant updatedAt;
    private List<ReleaseDO> releases;

    @Data
    public static class ReleaseDO {
        private String id;
        private long revision;
        private String documentJson;
        private Instant createdAt;
    }
}
