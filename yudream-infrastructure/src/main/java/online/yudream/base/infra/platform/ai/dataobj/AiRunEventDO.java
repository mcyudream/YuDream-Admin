package online.yudream.base.infra.platform.ai.dataobj;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Map;

@Data
@Document("platformAiRunEvent")
@CompoundIndex(name = "runCursor", def = "{'runId':1,'cursor':1}", unique = true)
public class AiRunEventDO {
    @Id private String id;
    private String runId;
    private long cursor;
    private String event;
    private String action;
    private String module;
    private String traceId;
    private Instant timestamp;
    private Map<String, Object> payload;
}
