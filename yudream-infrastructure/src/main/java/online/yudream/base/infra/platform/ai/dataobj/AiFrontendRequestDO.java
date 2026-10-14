package online.yudream.base.infra.platform.ai.dataobj;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Map;

@Data
@Document("platformAiFrontendRequest")
public class AiFrontendRequestDO {
    @Id private String id;
    private String runId;
    private String toolCallId;
    private String name;
    private Map<String, Object> arguments;
    private Instant expiresAt;
    private String status;
    private Map<String, Object> response;
}
