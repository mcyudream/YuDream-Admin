package online.yudream.base.infra.platform.ai.dataobj;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Document("platformAiPublicRunGrant")
public class PublicAiRunGrantDO {
    @Id private String runId;
    private String tokenHash;
    private String sessionId;
    private String spaceSlug;
    private Instant expiresAt;
}
